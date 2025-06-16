// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.duplicateStringLiteral;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.search.LowLevelSearchUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.util.*;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.util.occurrences.BaseOccurrenceManager;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringSearcher;
import com.siyeh.ig.fixes.IntroduceConstantFix;
import com.siyeh.ig.style.UnnecessarilyQualifiedStaticUsageInspection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

import static com.intellij.codeInspection.options.OptPane.*;

public final class DuplicateStringLiteralInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final int MAX_FILES_TO_ON_THE_FLY_SEARCH = 10;

  @SuppressWarnings("WeakerAccess") public int MIN_STRING_LENGTH = 5;
  @SuppressWarnings("WeakerAccess") public boolean IGNORE_PROPERTY_KEYS;
  private static final @NonNls String BR = "<br>";

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitReferenceExpression(final @NotNull PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
        checkStringLiteralExpression(expression, holder, isOnTheFly);
      }
    };
  }

  @Override
  public @NotNull String getShortName() {
    return "DuplicateStringLiteralInspection";
  }

  private static @NotNull List<PsiLiteralExpression> findDuplicateLiterals(@NotNull StringLiteralSearchQuery query, @NotNull Project project) {
    final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    final List<String> words = ContainerUtil.filter(StringUtil.getWordsInStringLongestFirst(query.stringToFind), s -> s.length() >= query.minStringLength);
    if (words.isEmpty()) return Collections.emptyList();
    List<PsiLiteralExpression> foundExpressions = new SmartList<>();

    CacheManager.getInstance(project).processVirtualFilesWithAllWords(words,
                                                                      UsageSearchContext.IN_STRINGS,
                                                                      scope,
                                                                      true, new Processor<>() {
        int filesWithLiterals;

        @Override
        public boolean process(VirtualFile f) {
          FileViewProvider viewProvider = PsiManager.getInstance(project).findViewProvider(f);
          // important: skip non-java files with given word in literal (IDEA-126201)
          if (viewProvider == null || viewProvider.getPsi(JavaLanguage.INSTANCE) == null) return true;
          PsiFile psiFile = viewProvider.getPsi(viewProvider.getBaseLanguage());
          if (psiFile != null) {
            List<PsiLiteralExpression> duplicateLiteralsInFile =
              findDuplicateLiteralsInFile(query.stringToFind, query.ignorePropertyKeys, psiFile);
            if (!duplicateLiteralsInFile.isEmpty()) {
              foundExpressions.addAll(duplicateLiteralsInFile);
              if (query.isOnFlySearch && ++filesWithLiterals >= MAX_FILES_TO_ON_THE_FLY_SEARCH) {
                return false;
              }
            }
          }
          return true;
        }
      });
    return foundExpressions;
  }

  private static @NotNull List<PsiLiteralExpression> findDuplicateLiteralsInFile(@NotNull String stringToFind, boolean ignorePropertyKeys, @NotNull PsiFile file) {
    ProgressManager.checkCanceled();
    CharSequence text = file.getViewProvider().getContents();
    StringSearcher searcher = new StringSearcher(stringToFind, true, true);

    List<PsiLiteralExpression> foundExpr = new SmartList<>();
    LowLevelSearchUtil.processTexts(text, 0, text.length(), searcher, offset -> {
      PsiElement element = file.findElementAt(offset);
      if (element == null || !(element.getParent() instanceof PsiLiteralExpression expression)) return true;
      if (Comparing.equal(stringToFind, expression.getValue()) && shouldCheck(expression, ignorePropertyKeys)) {
        foundExpr.add(expression);
      }
      return true;
    });
    return foundExpr;
  }

  private void checkStringLiteralExpression(final @NotNull PsiLiteralExpression originalExpression,
                                            @NotNull ProblemsHolder holder,
                                            final boolean isOnTheFly) {
    PsiExpression[] foundExpr = getDuplicateLiterals(holder.getProject(), originalExpression, isOnTheFly);
    if (foundExpr.length == 0) return;
    Set<PsiClass> classes = new HashSet<>();
    for (PsiElement aClass : foundExpr) {
      if (aClass == originalExpression) continue;
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

    List<PsiClass> tenClassesMost = ContainerUtil.getFirstItems(Arrays.asList(classes.toArray(PsiClass.EMPTY_ARRAY)),
                                                                MAX_FILES_TO_ON_THE_FLY_SEARCH);

    String classList;
    if (isOnTheFly) {
      classList = StringUtil.join(tenClassesMost, aClass -> {
        final boolean thisFile = aClass.getContainingFile() == originalExpression.getContainingFile();
        return "&nbsp;&nbsp;&nbsp;'<b>" + aClass.getQualifiedName() + "</b>'" +
               (thisFile ? " " + JavaI18nBundle.message("inspection.duplicates.message.in.this.file") : "");
      }, ", " + BR);
    }
    else {
      classList = StringUtil.join(tenClassesMost, aClass -> "'" + aClass.getQualifiedName() + "'", ", ");
    }

    if (classes.size() > tenClassesMost.size()) {
      classList += BR + JavaI18nBundle.message("inspection.duplicates.message.more", classes.size() - 10);
    }

    String msg = JavaI18nBundle.message("inspection.duplicates.message", classList);

    Collection<LocalQuickFix> fixes = new SmartList<>();
    if (isOnTheFly) {
      fixes.add(new IntroduceLiteralConstantFix());
      fixes.add(new NavigateToOccurrencesFix(originalExpression));
    }
    createReplaceFixes(foundExpr, originalExpression, fixes);
    LocalQuickFix[] array = fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
    holder.registerProblem(originalExpression, msg, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, array);
  }

  private PsiExpression @NotNull [] getDuplicateLiterals(@NotNull Project project, @NotNull PsiLiteralExpression place, boolean isOnTheFly) {
    Object value = place.getValue();
    if (!(value instanceof String stringToFind)) return PsiExpression.EMPTY_ARRAY;
    if (!shouldCheck(place, IGNORE_PROPERTY_KEYS)) return PsiExpression.EMPTY_ARRAY;
    if (stringToFind.isEmpty()) return PsiExpression.EMPTY_ARRAY;
    Map<StringLiteralSearchQuery, PsiExpression[]> map = CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      Map<StringLiteralSearchQuery, PsiExpression[]> duplicates = ConcurrentFactoryMap.createMap(
        q -> {
          return findDuplicateLiterals(q, project).toArray(PsiExpression.EMPTY_ARRAY);
        });
      return CachedValueProvider.Result.create(duplicates, PsiModificationTracker.MODIFICATION_COUNT);
    });
    return map.get(new StringLiteralSearchQuery(stringToFind, IGNORE_PROPERTY_KEYS, MIN_STRING_LENGTH, isOnTheFly));
  }

  private static boolean shouldCheck(@NotNull PsiLiteralExpression expression, boolean ignorePropertyKeys) {
    if (ignorePropertyKeys && JavaI18nUtil.mustBePropertyKey(expression, null)) return false;
    return !SuppressManager.isSuppressedInspectionName(expression);
  }

  private static void createReplaceFixes(PsiExpression @NotNull [] foundExpr, @NotNull PsiLiteralExpression originalExpression,
                                         @NotNull Collection<? super LocalQuickFix> fixes) {
    for (PsiExpression expr : foundExpr) {
      if (expr == originalExpression) continue;
      PsiElement parent = expr.getParent();
      if (parent instanceof PsiField field) {
        if (field.getInitializer() == expr && field.hasModifierProperty(PsiModifier.STATIC)) {
          final PsiClass containingClass = field.getContainingClass();
          if (containingClass == null) continue;
          boolean isAccessible = JavaPsiFacade.getInstance(field.getProject()).getResolveHelper().isAccessible(field, originalExpression,
                                                                                                               containingClass);
          if (!isAccessible && containingClass.getQualifiedName() == null) {
            continue;
          }
          fixes.add(LocalQuickFix.from(new ReplaceFix(field, originalExpression)));
        }
      }
    }
  }

  private static @Nullable PsiReferenceExpression createReferenceTo(@NotNull PsiField constant) throws IncorrectOperationException {
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
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("MIN_STRING_LENGTH", JavaI18nBundle.message("inspection.duplicates.option"), 1, 10_000),
      checkbox("IGNORE_PROPERTY_KEYS", JavaI18nBundle.message("inspection.duplicates.option.report.propertykey.expressions"))
    );
  }

  private class IntroduceLiteralConstantFix extends IntroduceConstantFix {
    private IntroduceLiteralConstantFix() {
      super(JavaI18nBundle.message("introduce.constant.across.the.project"));
    }

    @Override
    protected void doIntroduce(@NotNull Project project, PsiExpression element) {
      PsiExpression[] literalExpressions = getDuplicateLiteralsUnderProgress(element);
      if (literalExpressions == null) return;
      introduceConstant(literalExpressions, project);
    }
  }

  private static final class ReplaceFix extends PsiUpdateModCommandAction<PsiLiteralExpression> {
    private final @IntentionName String myText;
    private final SmartPsiElementPointer<PsiField> myConst;

    private ReplaceFix(PsiField constant, PsiLiteralExpression originalExpression) {
      super(originalExpression);
      myText = JavaI18nBundle.message("inspection.duplicates.replace.quickfix", PsiFormatUtil
        .formatVariable(constant, PsiFormatUtilBase.SHOW_CONTAINING_CLASS |
                                    PsiFormatUtilBase.SHOW_FQ_NAME |
                                    PsiFormatUtilBase.SHOW_NAME,
                        PsiSubstitutor.EMPTY));
      myConst = SmartPointerManager.getInstance(constant.getProject()).createSmartPsiElementPointer(constant);
    }

    @Override
    protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiLiteralExpression element) {
      return Presentation.of(myText);
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiLiteralExpression literal, @NotNull ModPsiUpdater updater) {
      final PsiField myConstant = myConst.getElement();
      if (myConstant == null) return;
      final PsiExpression initializer = myConstant.getInitializer();
      if (!(initializer instanceof PsiLiteralExpression)) {
        return;
      }
      final PsiReferenceExpression reference = createReferenceTo(myConstant);
      if (reference != null) {
        final PsiReferenceExpression newReference = (PsiReferenceExpression)literal.replace(reference);
        if (UnnecessarilyQualifiedStaticUsageInspection.isUnnecessarilyQualifiedAccess(newReference, false, false, true)) {
          //remove qualifier
          newReference.getChildren()[0].delete();
        }
      }
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaI18nBundle.message("inspection.duplicates.replace.family.quickfix");
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
    public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      PsiExpression[] literalExpressions = getDuplicateLiteralsUnderProgress(startElement);
      if (literalExpressions == null) return;

      Usage[] usages = Stream.of(literalExpressions)
        .map(UsageInfo::new)
        .map(UsageInfo2UsageAdapter::new)
        .toArray(Usage[]::new);

      UsageViewPresentation presentation = new UsageViewPresentation();
      String title = JavaI18nBundle.message("inspection.duplicates.occurrences.view.title", ((PsiLiteralExpression)startElement).getValue());
      presentation.setUsagesString(title);
      presentation.setTabName(title);
      presentation.setTabText(title);
      presentation.setShowCancelButton(true);
      UsageView view = UsageViewManager.getInstance(project).showUsages(new UsageTarget[]{new PsiElement2UsageTargetAdapter(startElement, false) {
        @Override
        public String getPresentableText() {
          return "String literal: '" + ((PsiLiteralExpression)startElement).getValue() + "'";
        }
      }}, usages, presentation);
      view.addButtonToLowerPane(() -> {
        introduceConstant(literalExpressions, project);
        view.close();
      }, JavaI18nBundle.message("introduce.constant.across.the.project"));
    }

    @Override
    public @NotNull String getText() {
      return getFamilyName();
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return JavaI18nBundle.message("inspection.duplicates.navigate.to.occurrences");
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      final PsiElement element = previewDescriptor.getPsiElement();
      if (element instanceof PsiLiteralExpression literal) {
        final PsiExpression[] duplicates = getDuplicateLiterals(project, literal, true);
        if (duplicates.length < 10) {
          return new IntentionPreviewInfo.Html(JavaI18nBundle.message(
            "inspection.duplicates.navigate.to.occurrences.preview",
            duplicates.length,
            literal.getText()
          ));
        }
        return new IntentionPreviewInfo.Html(JavaI18nBundle.message(
          "inspection.duplicates.navigate.to.many.occurrences.preview",
          duplicates.length,
          literal.getText()
        ));
      }
      return IntentionPreviewInfo.EMPTY;
    }
  }

  private record StringLiteralSearchQuery(@NotNull String stringToFind, boolean ignorePropertyKeys, int minStringLength,
                                          boolean isOnFlySearch) {
  }

  private PsiExpression @Nullable [] getDuplicateLiteralsUnderProgress(@NotNull PsiElement literalExpression) {
    if (!(literalExpression instanceof PsiLiteralExpression)) return null;
    Project project = literalExpression.getProject();
    ThrowableComputable<PsiExpression[], RuntimeException> findDuplicatesComputable = () -> {
      return ReadAction.compute(() -> getDuplicateLiterals(project,
                                                           (PsiLiteralExpression)literalExpression,
                                                           false /* here we want find all the expressions */));
    };
    if (IntentionPreviewUtils.isPreviewElement(literalExpression)) {
      PsiExpression[] expressions = findDuplicatesComputable.compute();
      if (expressions == null) return null;
      PsiFile file = literalExpression.getContainingFile();
      return ContainerUtil.map2Array(expressions, PsiExpression.EMPTY_ARRAY, e -> PsiTreeUtil.findSameElementInCopy(e, file));
    }
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(findDuplicatesComputable, JavaI18nBundle.message("progress.title.searching.for.duplicates.of.0", ((PsiLiteralExpression)literalExpression).getValue()), true, project);
  }

  private static void introduceConstant(PsiExpression @NotNull [] expressions, @NotNull Project project) {
    new IntroduceConstantHandler() {
      @Override
      protected OccurrenceManager createOccurrenceManager(PsiExpression selectedExpr, PsiClass parentClass) {
        return new BaseOccurrenceManager(occurrence -> true) {
          @Override
          protected PsiExpression @NotNull [] defaultOccurrences() {
            return expressions;
          }

          @Override
          protected PsiExpression @NotNull [] findOccurrences() {
            return expressions;
          }
        };
      }
    }.invoke(project, expressions);
  }
}
