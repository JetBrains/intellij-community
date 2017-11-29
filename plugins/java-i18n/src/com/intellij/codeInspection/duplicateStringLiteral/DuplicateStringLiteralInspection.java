/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.LowLevelSearchUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.*;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.util.occurrences.BaseOccurrenceManager;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringSearcher;
import com.siyeh.ig.style.UnnecessarilyQualifiedStaticUsageInspection;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.*;
import java.util.stream.Stream;

public class DuplicateStringLiteralInspection extends BaseLocalInspectionTool {
  @SuppressWarnings("WeakerAccess") public int MIN_STRING_LENGTH = 5;
  @SuppressWarnings("WeakerAccess") public boolean IGNORE_PROPERTY_KEYS;
  @NonNls private static final String BR = "<br>";

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
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

  @NotNull
  private Set<PsiFile> getCandidateFiles(@NotNull String stringToFind, @NotNull Project project) {
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    final PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(project);
    final List<String> words = StringUtil.getWordsInStringLongestFirst(stringToFind);
    if (words.isEmpty()) return Collections.emptySet();

    Set<PsiFile> resultFiles = null;
    for (String word : words) {
      if (word.length() < MIN_STRING_LENGTH) {
        continue;
      }
      ProgressManager.checkCanceled();
      final Set<PsiFile> files = new THashSet<>();
      Processor<PsiFile> processor = Processors.cancelableCollectProcessor(files);
      searchHelper.processAllFilesWithWordInLiterals(word, scope, processor);
      if (resultFiles == null) {
        resultFiles = files;
      }
      else {
        resultFiles.retainAll(files);
      }
      if (resultFiles.isEmpty()) return Collections.emptySet();
    }
    return resultFiles != null ? resultFiles : Collections.emptySet();
  }

  @NotNull
  private List<PsiLiteralExpression> findDuplicateLiterals(@NotNull String stringToFind, @NotNull Project project) {
    Set<PsiFile> resultFiles = getCandidateFiles(stringToFind, project);
    if (resultFiles.isEmpty()) return Collections.emptyList();
    List<PsiLiteralExpression> foundExpr = new ArrayList<>();

    for (final PsiFile file : resultFiles) {
      ProgressManager.checkCanceled();
      FileViewProvider viewProvider = file.getViewProvider();
      // important: skip non-java files with given word in literal (IDEA-126201)
      if (viewProvider.getPsi(JavaLanguage.INSTANCE) == null) continue;
      CharSequence text = viewProvider.getContents();
      StringSearcher searcher = new StringSearcher(stringToFind, true, true);

      LowLevelSearchUtil.processTextOccurrences(text, 0, text.length(), searcher, ProgressManager.getInstance().getProgressIndicator(), offset -> {
        PsiElement element = file.findElementAt(offset);
        if (element == null || !(element.getParent() instanceof PsiLiteralExpression)) return true;
        PsiLiteralExpression expression = (PsiLiteralExpression)element.getParent();
        if (Comparing.equal(stringToFind, expression.getValue()) && shouldCheck(expression)) {
          foundExpr.add(expression);
        }
        return true;
      });
    }
    return foundExpr;
  }

  private void checkStringLiteralExpression(@NotNull final PsiLiteralExpression originalExpression,
                                            @NotNull ProblemsHolder holder,
                                            final boolean isOnTheFly) {
    List<PsiLiteralExpression> foundExpr = getDuplicateLiterals(holder.getProject(), originalExpression);
    if (foundExpr.isEmpty()) return;
    Set<PsiClass> classes = new THashSet<>();
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
      fixes.add(createIntroduceConstFix(foundExpr, originalExpression));
      fixes.add(new NavigateToOccurrencesFix(originalExpression));
    }
    createReplaceFixes(foundExpr, originalExpression, fixes);
    LocalQuickFix[] array = fixes.toArray(new LocalQuickFix[fixes.size()]);
    holder.registerProblem(originalExpression, msg, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, array);
  }

  @NotNull
  private List<PsiLiteralExpression> getDuplicateLiterals(@NotNull Project project, @NotNull PsiLiteralExpression place) {
    Object value = place.getValue();
    if (!(value instanceof String)) return Collections.emptyList();
    if (!shouldCheck(place)) return Collections.emptyList();
    String stringToFind = (String)value;
    if (stringToFind.isEmpty()) return Collections.emptyList();
    Map<String, List<PsiLiteralExpression>> map = CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      Map<String, List<PsiLiteralExpression>> duplicates = ConcurrentFactoryMap.createMap(
        s -> Collections.unmodifiableList(findDuplicateLiterals(s, project)));
      return CachedValueProvider.Result.create(duplicates, PsiModificationTracker.MODIFICATION_COUNT);
    });
    return ContainerUtil.filter(map.get(stringToFind), literal -> literal != place);
  }

  private boolean shouldCheck(@NotNull PsiLiteralExpression expression) {
    if (IGNORE_PROPERTY_KEYS && JavaI18nUtil.mustBePropertyKey(expression, new THashMap<>())) return false;
    return !SuppressManager.isSuppressedInspectionName(expression);
  }

  private static void createReplaceFixes(@NotNull List<PsiLiteralExpression> foundExpr, @NotNull PsiLiteralExpression originalExpression,
                                         @NotNull Collection<LocalQuickFix> fixes) {
    Set<PsiField> constants = new THashSet<>();
    for (Iterator<PsiLiteralExpression> iterator = foundExpr.iterator(); iterator.hasNext();) {
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

  @NotNull
  private static LocalQuickFix createIntroduceConstFix(@NotNull List<PsiLiteralExpression> foundExpr, @NotNull PsiLiteralExpression originalExpression) {
    final PsiLiteralExpression[] expressions = foundExpr.toArray(new PsiLiteralExpression[foundExpr.size() + 1]);
    expressions[foundExpr.size()] = originalExpression;

    return new IntroduceLiteralConstantFix(expressions);
  }

  @Nullable
  private static PsiReferenceExpression createReferenceTo(@NotNull PsiField constant) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(constant.getProject()).getElementFactory();
    PsiReferenceExpression reference = (PsiReferenceExpression)factory.createExpressionFromText("XXX." + constant.getName(), null);
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
    optionsPanel.myIgnorePropertyKeyExpressions.addActionListener(
      e -> IGNORE_PROPERTY_KEYS = optionsPanel.myIgnorePropertyKeyExpressions.isSelected());
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

    IntroduceLiteralConstantFix(final PsiLiteralExpression[] expressions) {
      myExpressions = new SmartPsiElementPointer[expressions.length];
      for(int i=0; i<expressions.length; i++) {
        PsiExpression expression = expressions[i];
        myExpressions[i] = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression);
      }
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionsBundle.message("introduce.constant.across.the.project");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      applyFix(project);
    }

    private void applyFix(@NotNull Project project) {
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
          return new BaseOccurrenceManager(occurrence -> true) {
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
    }
  }

  private static class ReplaceFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final String myText;
    private final SmartPsiElementPointer<PsiField> myConst;

    private ReplaceFix(PsiField constant, PsiLiteralExpression originalExpression) {
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
      try {
        final PsiReferenceExpression reference = createReferenceTo(myConstant);
        if (reference != null) {
          final PsiReferenceExpression newReference = (PsiReferenceExpression)myOriginalExpression.replace(reference);
          if (UnnecessarilyQualifiedStaticUsageInspection.isUnnecessarilyQualifiedAccess(newReference, false, false, true)) {
            //remove qualifier
            newReference.getChildren()[0].delete();
          }
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

  private class NavigateToOccurrencesFix extends LocalQuickFixOnPsiElement {
    NavigateToOccurrencesFix(PsiLiteralExpression representative) {
      super(representative);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      if (!(startElement instanceof PsiLiteralExpression)) return;

      PsiLiteralExpression literal = (PsiLiteralExpression)startElement;
      List<PsiLiteralExpression> duplicates = getDuplicateLiterals(file.getProject(), literal);
      PsiLiteralExpression[] literalExpressions = Stream.concat(duplicates.stream(), Stream.of(literal)).toArray(PsiLiteralExpression[]::new);
      Usage[] usages = Stream.of(literalExpressions)
        .map(UsageInfo::new)
        .map(UsageInfo2UsageAdapter::new)
        .toArray(Usage[]::new);

      UsageViewPresentation presentation = new UsageViewPresentation();
      String title = InspectionsBundle.message("inspection.duplicates.occurrences.view.title", literal.getValue());
      presentation.setUsagesString(title);
      presentation.setTabName(title);
      presentation.setTabText(title);
      presentation.setShowCancelButton(true);
      UsageView view = UsageViewManager.getInstance(project).showUsages(new UsageTarget[]{new PsiElement2UsageTargetAdapter(literal) {
        @Override
        public String getPresentableText() {
          return "String literal: \'" + literal.getValue() + "\'";
        }
      }}, usages, presentation);
      view.addButtonToLowerPane(() -> {
        new IntroduceLiteralConstantFix(literalExpressions).applyFix(project);
        view.close();
      }, InspectionsBundle.message("introduce.constant.across.the.project"));
    }

    @NotNull
    @Override
    public String getText() {
      return getFamilyName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.duplicates.navigate.to.occurrences");
    }
  }
}
