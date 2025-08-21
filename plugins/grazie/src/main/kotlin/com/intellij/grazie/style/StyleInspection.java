package com.intellij.grazie.style;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection;
import com.intellij.grazie.text.*;
import com.intellij.grazie.text.TextContent.TextDomain;
import com.intellij.grazie.utils.HighlightingUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.grazie.ide.inspection.grammar.GrazieInspection.ignoreGrammarChecking;
import static com.intellij.grazie.ide.inspection.grammar.GrazieInspection.sortByPriority;

public class StyleInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    PsiFile file = session.getFile();
    if (ignoreGrammarChecking(file) || InspectionProfileManager.hasTooLowSeverity(session, this)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    Set<TextDomain> domains = HighlightingUtil.checkedDomains();
    Function1<PsiElement, Boolean> areChecksDisabled = GrazieInspection.getDisabledChecker(file);

    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiWhiteSpace || areChecksDisabled.invoke(element)) return;

        checkSpecificTexts(element, domains, holder, session);
        if (element == file) {
          checkTextLevel(file, holder);
        }
      }
    };
  }

  private static void checkTextLevel(PsiFile file, ProblemsHolder holder) {
    TreeRuleChecker.checkTextLevelProblems(file)
      .stream()
      .filter(TextProblem::isStyleLike)
      .forEach(problem -> reportProblem(problem, holder));
  }

  private static void checkSpecificTexts(PsiElement element,
                                         Set<TextDomain> domains,
                                         ProblemsHolder holder,
                                         LocalInspectionToolSession session) {
    var texts = sortByPriority(TextExtractor.findUniqueTextsAt(element, domains), session.getPriorityRange());
    if (HighlightingUtil.isTooLargeText(texts)) return;
    texts.forEach(text -> analyzeText(text, holder));
  }

  private static void analyzeText(TextContent text, ProblemsHolder holder) {
    CheckerRunner runner = new CheckerRunner(text);
    runner.run()
      .stream()
      .filter(TextProblem::isStyleLike)
      .forEach(problem -> reportProblem(problem, holder));
  }

  private static void reportProblem(TextProblem problem, ProblemsHolder holder) {
    new CheckerRunner(problem.getText()).toProblemDescriptors(problem, holder.isOnTheFly())
      .forEach(holder::registerProblem);
  }
}
