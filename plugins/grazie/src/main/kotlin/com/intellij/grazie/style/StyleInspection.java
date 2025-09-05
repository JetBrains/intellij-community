package com.intellij.grazie.style;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.grazie.text.CheckerRunner;
import com.intellij.grazie.text.TextContent.TextDomain;
import com.intellij.grazie.text.TextProblem;
import com.intellij.grazie.text.TreeRuleChecker;
import com.intellij.grazie.utils.HighlightingUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.grazie.ide.inspection.grammar.GrazieInspection.*;

public class StyleInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    PsiFile file = session.getFile();
    if (ignoreGrammarChecking(file) || InspectionProfileManager.hasTooLowSeverity(session, this)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    Set<TextDomain> checkedDomains = HighlightingUtil.checkedDomains();
    Function1<PsiElement, Boolean> areChecksDisabled = getDisabledChecker(file);

    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiWhiteSpace || areChecksDisabled.invoke(element)) return;

        inspectElement(element, TextProblem::isStyleLike, session, holder, checkedDomains);

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

  private static void reportProblem(TextProblem problem, ProblemsHolder holder) {
    new CheckerRunner(problem.getText()).toProblemDescriptors(problem, holder.isOnTheFly())
      .forEach(holder::registerProblem);
  }
}
