/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.duplicateStringLiteral;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.LowLevelSearchUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.util.occurences.BaseOccurenceManager;
import com.intellij.refactoring.util.occurences.OccurenceFilter;
import com.intellij.refactoring.util.occurences.OccurenceManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class DuplicateStringLiteralInspection extends BaseLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.DuplicateStringLiteralInspection");
  private JTextField myMinStringLengthField;
  @SuppressWarnings({"WeakerAccess"}) public int MIN_STRING_LENGTH = 5;
  @SuppressWarnings({"WeakerAccess"}) public boolean IGNORE_PROPERTY_KEYS = false;
  private JPanel myPanel;
  private JCheckBox myIgnorePropertyKeyExpressions;
  @NonNls private static final String BR = "<br>";
  private boolean UIInitialized = false;

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override public void visitReferenceExpression(final PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
        checkStringLiteralExpression(expression, holder, isOnTheFly);
      }
    };
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.duplicates.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
  }

  @NotNull
  public String getShortName() {
    return "DuplicateStringLiteralInspection";
  }

  private void checkStringLiteralExpression(final PsiLiteralExpression originalExpression,
                                            ProblemsHolder holder,
                                            final boolean isOnTheFly) {
    Object value = originalExpression.getValue();
    if (!(value instanceof String)) return;
    if (!shouldCheck(originalExpression)) return;
    final String stringToFind = (String)value;
    if (stringToFind.length() == 0) return;
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(originalExpression.getProject());
    final PsiSearchHelper searchHelper = originalExpression.getManager().getSearchHelper();
    final List<String> words = StringUtil.getWordsIn(stringToFind);
    if (words.isEmpty()) return;
    // put longer strings first
    Collections.sort(words, new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return o2.length() - o1.length();
      }
    });

    Set<PsiFile> resultFiles = null;
    for (String word : words) {
      if (word.length() < MIN_STRING_LENGTH) {
        continue;
      }
      ProgressManager.checkCanceled();
      final Set<PsiFile> files = new THashSet<PsiFile>();
      searchHelper.processAllFilesWithWordInLiterals(word, scope, new CommonProcessors.CollectProcessor<PsiFile>(files));
      if (resultFiles == null) {
        resultFiles = files;
      }
      else {
        resultFiles.retainAll(files);
      }
      if (resultFiles.isEmpty()) return;
    }
    if (resultFiles == null || resultFiles.isEmpty()) return;
    final List<PsiExpression> foundExpr = new ArrayList<PsiExpression>();
    for (PsiFile file : resultFiles) {
      ProgressManager.checkCanceled();
      CharSequence text = file.getViewProvider().getContents();
      StringSearcher searcher = new StringSearcher(stringToFind, true, true);
      for (int offset = LowLevelSearchUtil.searchWord(text, 0, text.length(), searcher);
           offset >= 0;
           offset = LowLevelSearchUtil.searchWord(text, offset + searcher.getPattern().length(), text.length(), searcher)
        ) {
        ProgressManager.checkCanceled();
        PsiElement element = file.findElementAt(offset);
        if (element == null || !(element.getParent() instanceof PsiLiteralExpression)) continue;
        PsiLiteralExpression expression = (PsiLiteralExpression)element.getParent();
        if (expression != originalExpression && Comparing.equal(stringToFind, expression.getValue()) && shouldCheck(expression)) {
          foundExpr.add(expression);
        }
      }
    }
    if (foundExpr.isEmpty()) return;
    Set<PsiClass> classes = new THashSet<PsiClass>();
    for (PsiElement aClass : foundExpr) {
      ProgressManager.checkCanceled();
      do {
        aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
      }
      while (aClass != null && ((PsiClass)aClass).getQualifiedName() == null);
      if (aClass != null) {
        classes.add((PsiClass)aClass);
      }
    }
    if (classes.isEmpty()) return;

    List<PsiClass> tenClassesMost = Arrays.asList(classes.toArray(new PsiClass[classes.size()]));
    if (tenClassesMost.size() > 10) {
      tenClassesMost = tenClassesMost.subList(0, 10);
    }

    String classList;
    if (isOnTheFly) {
      classList = StringUtil.join(tenClassesMost, new Function<PsiClass, String>() {
        public String fun(final PsiClass aClass) {
          final boolean thisFile = aClass.getContainingFile() == originalExpression.getContainingFile();
          //noinspection HardCodedStringLiteral
          return "&nbsp;&nbsp;&nbsp;'<b>" + aClass.getQualifiedName() + "</b>'" +
                 (thisFile ? " " + InspectionsBundle.message("inspection.duplicates.message.in.this.file") : "");
        }
      }, ", " + BR);
    }
    else {
      classList = StringUtil.join(tenClassesMost, new Function<PsiClass, String>() {
        public String fun(final PsiClass aClass) {
          return "'" + aClass.getQualifiedName() + "'";
        }
      }, ", ");
    }

    if (classes.size() > tenClassesMost.size()) {
      classList += BR + InspectionsBundle.message("inspection.duplicates.message.more", classes.size() - 10);
    }

    String msg = InspectionsBundle.message("inspection.duplicates.message", classList);

    Collection<LocalQuickFix> fixes = new SmartList<LocalQuickFix>();
    final LocalQuickFix introduceConstFix = createIntroduceConstFix(foundExpr, originalExpression);
    fixes.add(introduceConstFix);
    createReplaceFixes(foundExpr, originalExpression, fixes);
    LocalQuickFix[] array = fixes.toArray(new LocalQuickFix[fixes.size()]);
    holder.registerProblem(originalExpression, msg, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, array);
  }

  private boolean shouldCheck(final PsiLiteralExpression expression) {
    if (IGNORE_PROPERTY_KEYS && JavaI18nUtil.mustBePropertyKey(expression, new THashMap<String, Object>())) return false;
    PsiAnnotation annotation = PsiTreeUtil.getParentOfType(expression, PsiAnnotation.class, true, PsiCodeBlock.class, PsiField.class);
    return annotation == null || !"java.lang.SuppressWarnings".equals(annotation.getQualifiedName());
  }

  private static void createReplaceFixes(final List<PsiExpression> foundExpr, final PsiLiteralExpression originalExpression,
                                         final Collection<LocalQuickFix> fixes) {
    Set<PsiField> constants = new THashSet<PsiField>();
    for (Iterator<PsiExpression> iterator = foundExpr.iterator(); iterator.hasNext();) {
      PsiExpression expression1 = iterator.next();
      if (expression1.getParent() instanceof PsiField) {
        final PsiField field = (PsiField)expression1.getParent();
        if (field.getInitializer() == expression1 && field.hasModifierProperty(PsiModifier.FINAL) && field.hasModifierProperty(PsiModifier.STATIC)) {
          constants.add(field);
          iterator.remove();
        }
      }
    }
    for (final PsiField constant : constants) {
      final PsiClass containingClass = constant.getContainingClass();
      if (containingClass == null) continue;
      boolean isAccessible = JavaPsiFacade.getInstance(constant.getProject()).getResolveHelper() .isAccessible(constant, originalExpression,
                                                                                                            containingClass);
      if (!isAccessible && containingClass.getQualifiedName() == null) {
        continue;
      }
      final LocalQuickFix replaceQuickFix = new LocalQuickFix() {
        @NotNull
        public String getName() {
          return InspectionsBundle.message("inspection.duplicates.replace.quickfix", PsiFormatUtil
            .formatVariable(constant, PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_FQ_NAME | PsiFormatUtil.SHOW_NAME,
                            PsiSubstitutor.EMPTY));
        }

        public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
          if (!CodeInsightUtilBase.prepareFileForWrite(originalExpression.getContainingFile())) return;
          try {
            final PsiReferenceExpression reference = createReferenceTo(constant, originalExpression);
            if (reference != null) {
              originalExpression.replace(reference);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }

        @NotNull
        public String getFamilyName() {
          return InspectionsBundle.message("inspection.duplicates.replace.family.quickfix");
        }
      };
      fixes.add(replaceQuickFix);
    }
  }

  private static LocalQuickFix createIntroduceConstFix(final List<PsiExpression> foundExpr, final PsiLiteralExpression originalExpression) {
    final PsiExpression[] expressions = foundExpr.toArray(new PsiExpression[foundExpr.size() + 1]);
    expressions[foundExpr.size()] = originalExpression;

    return new IntroduceLiteralConstantFix(expressions);
  }

  @Nullable
  private static PsiReferenceExpression createReferenceTo(final PsiField constant, final PsiLiteralExpression context) throws IncorrectOperationException {
    PsiReferenceExpression reference = (PsiReferenceExpression)JavaPsiFacade.getInstance(constant.getProject()).getElementFactory()
      .createExpressionFromText(constant.getName(), context);
    if (reference.isReferenceTo(constant)) return reference;
    reference = (PsiReferenceExpression)JavaPsiFacade.getInstance(constant.getProject()).getElementFactory()
      .createExpressionFromText("XXX."+constant.getName(), null);
    final PsiReferenceExpression classQualifier = (PsiReferenceExpression)reference.getQualifierExpression();
    PsiClass containingClass = constant.getContainingClass();
    if (containingClass.getQualifiedName() == null) return null;
    classQualifier.bindToElement(containingClass);

    if (reference.isReferenceTo(constant)) return reference;
    return null;
  }

  public boolean isEnabledByDefault() {
    return false;
  }

  public JComponent createOptionsPanel() {
    if (!UIInitialized) {
      UIInitialized = true;
      myIgnorePropertyKeyExpressions.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          IGNORE_PROPERTY_KEYS = myIgnorePropertyKeyExpressions.isSelected();
        }
      });
      myMinStringLengthField.getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(final DocumentEvent e) {
          try {
            MIN_STRING_LENGTH = Integer.parseInt(myMinStringLengthField.getText());
          }
          catch (NumberFormatException e1) {
          }
        }
      });
    }
    myIgnorePropertyKeyExpressions.setSelected(IGNORE_PROPERTY_KEYS);
    myMinStringLengthField.setText(Integer.toString(MIN_STRING_LENGTH));
    return myPanel;
  }

  private static class IntroduceLiteralConstantFix implements LocalQuickFix {
    private final SmartPsiElementPointer[] myExpressions;

    public IntroduceLiteralConstantFix(final PsiExpression[] expressions) {
      myExpressions = new SmartPsiElementPointer[expressions.length];
      for(int i=0; i<expressions.length; i++) {
        myExpressions [i] = SmartPointerManager.getInstance(expressions [i].getProject()).createLazyPointer(expressions [i]);
      }
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("introduce.constant.across.the.project");
    }

    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          final List<PsiExpression> expressions = new ArrayList<PsiExpression>();
          for(SmartPsiElementPointer ptr: myExpressions) {
            final PsiElement element = ptr.getElement();
            if (element != null) {
              expressions.add((PsiExpression) element);
            }
          }
          final PsiExpression[] expressionArray = expressions.toArray(new PsiExpression[expressions.size()]);
          final IntroduceConstantHandler handler = new IntroduceConstantHandler() {
            protected OccurenceManager createOccurenceManager(PsiExpression selectedExpr, PsiClass parentClass) {
              final OccurenceFilter filter = new OccurenceFilter() {
                public boolean isOK(PsiExpression occurence) {
                  return true;
                }
              };
              return new BaseOccurenceManager(filter) {
                protected PsiExpression[] defaultOccurences() {
                  return expressionArray;
                }

                protected PsiExpression[] findOccurences() {
                  return expressionArray;
                }
              };
            }
          };
          handler.invoke(project, expressionArray);
        }
      });
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }
}
