package com.intellij.grazie.style;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.grazie.text.TextProblem;
import com.intellij.grazie.utils.HighlightingUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.containers.ContainerUtil;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.grazie.utils.CloudUtilsKt.isFunctionallyDisabled;

public class StyleInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    if (isFunctionallyDisabled()) return PsiElementVisitor.EMPTY_VISITOR;
    if (isOnTheFly) return PsiElementVisitor.EMPTY_VISITOR;

    PsiFile file = holder.getFile();
    if (GrazieInspection.Companion.ignoreGrammarChecking(file)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    Set<TextContent.TextDomain> domains = HighlightingUtil.checkedDomains();
    Function1<PsiElement, Boolean> areChecksDisabled = GrazieInspection.getDisabledChecker(file);

    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiWhiteSpace || areChecksDisabled.invoke(element)) return;

        var texts = TextExtractor.findUniqueTextsAt(element, domains);
        if (HighlightingUtil.isTooLargeText(texts)) return;

        for (TextContent text : texts) {
          PsiElement parent = text.getCommonParent();
          int parentStart = parent.getTextRange().getStartOffset();
          for (Pair<TextRange, TextProblem> pair : ContainerUtil.flatten(StyleAnnotator.analyze(text).values())) {
            TextProblem problem = pair.second;
            LocalQuickFix[] fixes = StyleAnnotator.getReplacementFixes(problem).toArray(LocalQuickFix.EMPTY_ARRAY);
            holder.registerProblem(parent, pair.first.shiftLeft(parentStart), problem.getDescriptionTemplate(false), fixes);
          }
        }
      }
    };
  }
}
