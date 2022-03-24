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
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.SpellCheckerSeveritiesProvider;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;

public class CommitAnnotator implements Annotator {

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
    List<TextChecker> checkers = TextChecker.allCheckers();
    CheckerRunner runner = new CheckerRunner(text);
    runner.run(checkers, problem -> {
      if (problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE) &&
          Text.isSingleSentence(Text.findParagraphRange(text, problem.getReplacementRange()).subSequence(text))) {
        return null;
      }

      List<ProblemDescriptor> descriptors = runner.toProblemDescriptors(problem, true);
      if (descriptors.isEmpty()) return null;

      String message = problem.getDescriptionTemplate(true);
      String tooltip = problem.getTooltipTemplate();
      LocalQuickFix[] fixes = runner.toFixes(problem, descriptors.get(0));

      for (TextRange range : problem.getHighlightRanges()) {
        AnnotationBuilder annotation = holder
          .newAnnotation(HighlightSeverity.WARNING, message)
          .tooltip(tooltip)
          .textAttributes(SpellCheckerSeveritiesProvider.TYPO_KEY)
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
