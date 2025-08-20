package com.intellij.grazie.style;

import ai.grazie.nlp.langs.Language;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.grazie.grammar.LanguageToolChecker;
import com.intellij.grazie.ide.TextProblemSeverities;
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection;
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieReplaceTypoQuickFix;
import com.intellij.grazie.jlanguage.Lang;
import com.intellij.grazie.text.*;
import com.intellij.grazie.text.TreeRuleChecker.TreeProblem;
import com.intellij.grazie.utils.HighlightingUtil;
import com.intellij.grazie.utils.IdeUtils;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class StyleAnnotator implements Annotator {
  private static final Key<Pair<Long, Map<PsiElement, List<Pair<TextRange, TextProblem>>>>> CACHE_KEY =
    Key.create("grazie.pro.style.highlighting");
  @SuppressWarnings("UnresolvedPluginConfigReference")
  private static final String GRAZIE_INSPECTION_ID = "GrazieInspection";

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    PsiFile file = holder.getCurrentAnnotationSession().getFile();
    if (GrazieInspection.Companion.ignoreGrammarChecking(file) || !file.isPhysical() ||
        !HighlightingUtil.shouldEnableHighlighting(file) ||
        HighlightingUtil.skipExpensivePrecommitAnalysis(file)) {
      return;
    }

    if (element instanceof PsiWhiteSpace || GrazieInspection.getDisabledChecker(file).invoke(element)) return;

    var profile = InspectionProfileManager.getInstance(file.getProject()).getCurrentProfile();
    var tools = profile.getToolsOrNull(GRAZIE_INSPECTION_ID, file.getProject());
    if (tools == null || !tools.isEnabled(file) || tools.getTool().getTool().isSuppressedFor(element)) {
      return;
    }

    checkSpecificTexts(element, holder);
    if (element == file) {
      checkTextLevel(file, holder);
    }
  }

  private static void checkTextLevel(PsiFile file, AnnotationHolder holder) {
    for (TreeProblem problem : TreeRuleChecker.checkTextLevelProblems(file)) {
      for (TextRange range : HighlightingUtil.getFileHighlightRanges(problem)) {
        reportProblem(holder, problem, range);
      }
    }
  }

  private static void checkSpecificTexts(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    var texts = TextExtractor.findTextsAt(element, GrazieInspection.checkedDomains());
    if (HighlightingUtil.isTooLargeText(texts)) return;

    for (TextContent text : texts) {
      for (Pair<TextRange, TextProblem> pair : analyze(text).getOrDefault(element, List.of())) {
        reportProblem(holder, pair.second, pair.first);
      }
    }
  }

  private static void reportProblem(AnnotationHolder holder, TextProblem problem, TextRange range) {
    List<ProblemDescriptor> descriptors = new CheckerRunner(problem.getText()).toProblemDescriptors(problem, true);
    LocalQuickFix[] fixes = (LocalQuickFix[])Objects.requireNonNull(descriptors.get(0).getFixes());

    var project = holder.getCurrentAnnotationSession().getFile().getProject();
    var attributes = IdeUtils.obtainTextAttributes(TextProblemSeverities.STYLE_SUGGESTION, project);

    AnnotationBuilder annotation = holder
      .newAnnotation(TextProblemSeverities.STYLE_SUGGESTION, problem.getDescriptionTemplate(true))
      .tooltip(problem.getTooltipTemplate())
      .textAttributes(attributes)
      .problemGroup(() -> GRAZIE_INSPECTION_ID)
      .range(range);
    for (QuickFix<?> fix : fixes) {
      annotation = annotation.withFix((IntentionAction)fix);
    }
    annotation.create();
  }

  public static List<LocalQuickFix> getReplacementFixes(TextProblem problem) {
    return GrazieReplaceTypoQuickFix.getReplacementFixes(problem, getUnderlineRanges(problem));
  }

  private static List<SmartPsiFileRange> getUnderlineRanges(TextProblem problem) {
    PsiFile file = problem.getText().getContainingFile();
    var spm = SmartPointerManager.getInstance(file.getProject());
    List<TextRange> ranges = HighlightingUtil.getFileHighlightRanges(problem);
    return ContainerUtil.map(ranges, r -> spm.createSmartPsiFileRangePointer(file, r));
  }

  public static Map<PsiElement, List<Pair<TextRange, TextProblem>>> analyze(TextContent text) {
    return HighlightingUtil.analyzeForAnnotator(text, CACHE_KEY, () -> {
      Language language = HighlightingUtil.detectPreferringEnglish(text.toString());
      Lang lang = language == null ? null : HighlightingUtil.findInstalledLang(language);
      if (lang == null || lang.getJLanguage() == null) return List.of();

      TextChecker ltStyleChecker = new TextChecker() {
        @Override
        public @NotNull Collection<? extends Rule> getRules(@NotNull Locale locale) {
          throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull Collection<? extends TextProblem> check(@NotNull TextContent extracted) {
          LanguageToolChecker ltChecker = new ExtensionPointName<>("com.intellij.grazie.textChecker")
            .findExtension(LanguageToolChecker.class);
          if (ltChecker != null) {
            return StreamEx.of(ltChecker.check(extracted))
              .filter(TextProblem::isStyleLike)
              .map(LTStyleProblemWrapper::new)
              .toList();
          }
          return List.of();
        }
      };

      List<Pair<TextRange, TextProblem>> result = new ArrayList<>();

      new CheckerRunner(text).run(List.of(new AsyncTreeRuleChecker.Style(), ltStyleChecker), problem -> {
        for (TextRange range : HighlightingUtil.getFileHighlightRanges(problem)) {
          result.add(Pair.create(range, problem));
        }
        return null;
      });

      return result;
    });
  }

  private static class LTStyleProblemWrapper extends TextProblem {
    private final LanguageToolChecker.Problem delegate;

    LTStyleProblemWrapper(LanguageToolChecker.Problem delegate) {
      super(delegate.getRule(), delegate.getText(), delegate.getHighlightRanges());
      this.delegate = delegate;
    }

    @Override
    public @NotNull String getTooltipTemplate() {
      return delegate.getTooltipTemplate();
    }

    @Override
    public @Nullable TextRange getPatternRange() {
      return delegate.getPatternRange();
    }

    @Override
    public @NotNull List<Suggestion> getSuggestions() {
      return delegate.getSuggestions();
    }

    @Override
    public @NotNull List<LocalQuickFix> getCustomFixes() {
      return delegate.getCustomFixes();
    }

    @Override
    public boolean fitsGroup(@NotNull RuleGroup group) {
      return delegate.fitsGroup(group);
    }

    @Override
    public @NotNull String getShortMessage() {
      return delegate.getShortMessage();
    }

    @Override
    public @NotNull @InspectionMessage String getDescriptionTemplate(boolean isOnTheFly) {
      return delegate.getDescriptionTemplate(isOnTheFly);
    }

    @Override
    public boolean isStyleLike() {
      return true;
    }
  }
}
