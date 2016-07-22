/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.LowLevelSearchUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.util.occurrences.BaseOccurrenceManager;
import com.intellij.refactoring.util.occurrences.OccurrenceFilter;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.*;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class DuplicateStringLiteralInspection extends BaseLocalInspectionTool {
  @SuppressWarnings({"WeakerAccess"}) public int MIN_STRING_LENGTH = 5;
  @SuppressWarnings({"WeakerAccess"}) public boolean IGNORE_PROPERTY_KEYS = false;
  @NonNls private static final String BR = "<br>";

  @Override
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

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.duplicates.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getShortName() {
    return "DuplicateStringLiteralInspection";
  }

  private void checkStringLiteralExpression(@NotNull final PsiLiteralExpression originalExpression,
                                            @NotNull ProblemsHolder holder,
                                            final boolean isOnTheFly) {
    Object value = originalExpression.getValue();
    if (!(value instanceof String)) return;
    if (!shouldCheck(originalExpression)) return;
    final String stringToFind = (String)value;
    if (stringToFind.length() == 0) return;
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(originalExpression.getProject());
    final PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(holder.getFile().getProject());
    final List<String> words = StringUtil.getWordsIn(stringToFind);
    if (words.isEmpty()) return;
    // put longer strings first
    Collections.sort(words, (o1, o2) -> o2.length() - o1.length());

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    Set<PsiFile> resultFiles = null;
    for (String word : words) {
      if (word.length() < MIN_STRING_LENGTH) {
        continue;
      }
      progress.checkCanceled();
      final Set<PsiFile> files = new THashSet<>();
      Processor<PsiFile> processor = Processors.cancelableCollectProcessor(files);
      searchHelper.processAllFilesWithWordInLiterals(word, scope, processor);
      if (resultFiles == null) {
        resultFiles = files;
      }
      else {
        resultFiles.retainAll(files);
      }
      if (resultFiles.isEmpty()) return;
    }

    if (resultFiles == null || resultFiles.isEmpty()) return;
    final List<PsiExpression> foundExpr = new ArrayList<>();

    for (final PsiFile file : resultFiles) {
      progress.checkCanceled();
      FileViewProvider viewProvider = file.getViewProvider();
      // important: skip non-java files with given word in literal (IDEA-126201)
      if (viewProvider.getPsi(JavaLanguage.INSTANCE) == null) continue;
      CharSequence text = viewProvider.getContents();
      StringSearcher searcher = new StringSearcher(stringToFind, true, true);

      LowLevelSearchUtil.processTextOccurrences(text, 0, text.length(), searcher, progress, new TIntProcedure() {
        @Override
        public boolean execute(int offset) {
          PsiElement element = file.findElementAt(offset);
          if (element == null || !(element.getParent() instanceof PsiLiteralExpression)) return true;
          PsiLiteralExpression expression = (PsiLiteralExpression)element.getParent();
          if (expression != originalExpression && Comparing.equal(stringToFind, expression.getValue()) && shouldCheck(expression)) {
            foundExpr.add(expression);
          }
          return true;
        }
      });
    }
    if (foundExpr.isEmpty()) return;
    Set<PsiClass> classes = new THashSet<>();
    for (PsiElement aClass : foundExpr) {
      progress.checkCanceled();
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
      classList = StringUtil.join(tenClassesMost, aClass -> {
        final boolean thisFile = aClass.getContainingFile() == originalExpression.getContainingFile();
        //noinspection HardCodedStringLiteral
        return "&nbsp;&nbsp;&nbsp;'<b>" + aClass.getQualifiedName() + "</b>'" +
               (thisFile ? " " + InspectionsBundle.message("inspection.duplicates.message.in.this.file") : "");
      }, ", " + BR);
    }
    else {
      classList = StringUtil.join(tenClassesMost, aClass -> "'" + aClass.getQualifiedName() + "'", ", ");
    }

    if (classes.size() > tenClassesMost.size()) {
      classList += BR + InspectionsBundle.message("inspection.duplicates.message.more", classes.size() - 10);
    }

    String msg = InspectionsBundle.message("inspection.duplicates.message", classList);

    Collection<LocalQuickFix> fixes = new SmartList<>();
    if (isOnTheFly) {
      final LocalQuickFix introduceConstFix = createIntroduceConstFix(foundExpr, originalExpression);
      fixes.add(introduceConstFix);
    }
    createReplaceFixes(foundExpr, originalExpression, fixes);
    LocalQuickFix[] array = fixes.toArray(new LocalQuickFix[fixes.size()]);
    holder.registerProblem(originalExpression, msg, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, array);
  }

  private boolean shouldCheck(@NotNull PsiLiteralExpression expression) {
    if (IGNORE_PROPERTY_KEYS && JavaI18nUtil.mustBePropertyKey(expression, new THashMap<>())) return false;
    return !SuppressManager.isSuppressedInspectionName(expression);
  }

  private static void createReplaceFixes(final List<PsiExpression> foundExpr, final PsiLiteralExpression originalExpression,
                                         final Collection<LocalQuickFix> fixes) {
    Set<PsiField> constants = new THashSet<>();
    for (Iterator<PsiExpression> iterator = foundExpr.iterator(); iterator.hasNext();) {
      PsiExpression expression1 = iterator.next();
      PsiElement parent = expression1.getParent();
      if (parent instanceof PsiField) {
        final PsiField field = (PsiField)parent;
        if (field.getInitializer() == expression1 && field.hasModifierProperty(PsiModifier.STATIC)) {
          constants.add(field);
          iterator.remove();
        }
      }
    }
    for (final PsiField constant : constants) {
      final PsiClass containingClass = constant.getContainingClass();
      if (containingClass == null) continue;
      boolean isAccessible = JavaPsiFacade.getInstance(constant.getProject()).getResolveHelper().isAccessible(constant, originalExpression,
                                                                                                              containingClass);
      if (!isAccessible && containingClass.getQualifiedName() == null) {
        continue;
      }
      final LocalQuickFix replaceQuickFix = new ReplaceFix(constant, originalExpression);
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
    PsiElementFactory factory = JavaPsiFacade.getInstance(constant.getProject()).getElementFactory();
    PsiReferenceExpression reference = (PsiReferenceExpression)factory.createExpressionFromText(constant.getName(), context);
    if (reference.isReferenceTo(constant)) return reference;
    reference = (PsiReferenceExpression)factory.createExpressionFromText("XXX." + constant.getName(), null);
    final PsiReferenceExpression classQualifier = (PsiReferenceExpression)reference.getQualifierExpression();
    PsiClass containingClass = constant.getContainingClass();
    if (containingClass.getQualifiedName() == null) return null;
    classQualifier.bindToElement(containingClass);

    if (reference.isReferenceTo(constant)) return reference;
    return null;
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  public JComponent createOptionsPanel() {
    final OptionsPanel optionsPanel = new OptionsPanel();
    optionsPanel.myIgnorePropertyKeyExpressions.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        IGNORE_PROPERTY_KEYS = optionsPanel.myIgnorePropertyKeyExpressions.isSelected();
      }
    });
    optionsPanel.myMinStringLengthField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(final DocumentEvent e) {
        try {
          MIN_STRING_LENGTH = Integer.parseInt(optionsPanel.myMinStringLengthField.getText());
        }
        catch (NumberFormatException ignored) {
        }
      }
    });
    optionsPanel.myIgnorePropertyKeyExpressions.setSelected(IGNORE_PROPERTY_KEYS);
    optionsPanel.myMinStringLengthField.setText(Integer.toString(MIN_STRING_LENGTH));
    return optionsPanel.myPanel;
  }

  public static class OptionsPanel {
     private JTextField myMinStringLengthField;
     private JPanel myPanel;
     private JCheckBox myIgnorePropertyKeyExpressions;
  }

  private static class IntroduceLiteralConstantFix implements LocalQuickFix {
    private final SmartPsiElementPointer[] myExpressions;

    public IntroduceLiteralConstantFix(final PsiExpression[] expressions) {
      myExpressions = new SmartPsiElementPointer[expressions.length];
      for(int i=0; i<expressions.length; i++) {
        PsiExpression expression = expressions[i];
        myExpressions[i] = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression);
      }
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionsBundle.message("introduce.constant.across.the.project");
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (project.isDisposed()) return;
        final List<PsiExpression> expressions = new ArrayList<>();
        for(SmartPsiElementPointer ptr: myExpressions) {
          final PsiElement element = ptr.getElement();
          if (element != null) {
            expressions.add((PsiExpression) element);
          }
        }
        final PsiExpression[] expressionArray = expressions.toArray(new PsiExpression[expressions.size()]);
        final IntroduceConstantHandler handler = new IntroduceConstantHandler() {
          @Override
          protected OccurrenceManager createOccurrenceManager(PsiExpression selectedExpr, PsiClass parentClass) {
            final OccurrenceFilter filter = new OccurrenceFilter() {
              @Override
              public boolean isOK(PsiExpression occurrence) {
                return true;
              }
            };
            return new BaseOccurrenceManager(filter) {
              @Override
              protected PsiExpression[] defaultOccurrences() {
                return expressionArray;
              }

              @Override
              protected PsiExpression[] findOccurrences() {
                return expressionArray;
              }
            };
          }
        };
        handler.invoke(project, expressionArray);
      });
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }
  }

  private static class ReplaceFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final String myText;
    private final SmartPsiElementPointer<PsiField> myConst;

    public ReplaceFix(PsiField constant, PsiLiteralExpression originalExpression) {
      super(originalExpression);
      myText = InspectionsBundle.message("inspection.duplicates.replace.quickfix", PsiFormatUtil
        .formatVariable(constant, PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                    PsiFormatUtilBase.SHOW_FQ_NAME |
                                    PsiFormatUtilBase.SHOW_NAME,
                        PsiSubstitutor.EMPTY));
      myConst = SmartPointerManager.getInstance(constant.getProject()).createSmartPsiElementPointer(constant);
    }

    @NotNull
    @Override
    public String getText() {
      return myText;
    }

    @Override
    public void invoke(@NotNull Project project,
                       @NotNull PsiFile file,
                       @Nullable("is null when called from inspection") Editor editor,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
      final PsiLiteralExpression myOriginalExpression = (PsiLiteralExpression)startElement;
      final PsiField myConstant = myConst.getElement();
      if (myConstant == null) return;
      final PsiExpression initializer = myConstant.getInitializer();
      if (!(initializer instanceof PsiLiteralExpression)) {
        return;
      }
      if (!FileModificationService.getInstance().prepareFileForWrite(myOriginalExpression.getContainingFile())) return;
      try {
        final PsiReferenceExpression reference = createReferenceTo(myConstant, myOriginalExpression);
        if (reference != null) {
          myOriginalExpression.replace(reference);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.duplicates.replace.family.quickfix");
    }
  }
}
