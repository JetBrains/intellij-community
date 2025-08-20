package com.intellij.grazie.ide.language.commit;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.text.*;
import com.intellij.grazie.utils.Text;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.intellij.grazie.ide.TextProblemSeverities.*;

final class CommitAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (!CommitMessage.isCommitMessage(element) || !GrazieConfig.Companion.get().getCheckingContext().isCheckInCommitMessagesEnabled()) {
      return;
    }

    for (TextContent text : TextExtractor.findTextsAt(element, EnumSet.of(TextContent.TextDomain.PLAIN_TEXT))) {
      checkText(holder, text);
    }
  }

  private static void checkText(AnnotationHolder holder, TextContent text) {
    List<TextChecker> checkers = new ArrayList<>(TextChecker.allCheckers());
    checkers.add(new AsyncTreeRuleChecker.Style());
    CheckerRunner runner = new CheckerRunner(text);
    runner.run(checkers, problem -> {
      if (problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE) &&
          Text.isSingleSentence(Text.findParagraphRange(text, problem.getHighlightRanges().get(0)).subSequence(text))) {
        return null;
      }

      List<ProblemDescriptor> descriptors = runner.toProblemDescriptors(problem, true);
      if (descriptors.isEmpty()) return null;

      String message = problem.getDescriptionTemplate(true);
      String tooltip = problem.getTooltipTemplate();
      LocalQuickFix[] fixes = runner.toFixes(problem, descriptors.get(0));

      for (TextRange range : problem.getHighlightRanges()) {
        boolean isStyle = problem.isStyleLike();
        AnnotationBuilder annotation = holder
          .newAnnotation(isStyle ? STYLE_SUGGESTION : GRAMMAR_ERROR, message)
          .tooltip(tooltip)
          .textAttributes(isStyle ? STYLE_SUGGESTION_ATTRIBUTES : GRAMMAR_ERROR_ATTRIBUTES)
          .range(text.textRangeToFile(range));
        for (QuickFix<?> fix : fixes) {
          annotation = annotation.withFix((IntentionAction)fix);
        }
        annotation.create();
      }
      return null;
    });
  }
}
