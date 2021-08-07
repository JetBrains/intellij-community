package com.intellij.grazie.ide.language.commit;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.QuickFix;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.text.*;
import com.intellij.grazie.utils.Text;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
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

    TextContent text = TextExtractor.findTextAt(element, EnumSet.of(TextContent.TextDomain.PLAIN_TEXT));
    if (text == null) return;

    List<TextChecker> checkers = TextChecker.allCheckers();
    CheckerRunner runner = new CheckerRunner(text);
    List<TextProblem> descriptors = runner.run(checkers);
    for (var problem : descriptors) {
      if (problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE) &&
          Text.isSingleSentence(Text.findParagraphRange(text, problem.getReplacementRange()).subSequence(text))) {
        continue;
      }

      String message = problem.getDescriptionTemplate(true);
      AnnotationBuilder annotation = holder
        .newAnnotation(HighlightSeverity.WARNING, message)
        .tooltip(message)
        .textAttributes(SpellCheckerSeveritiesProvider.TYPO_KEY)
        .range(text.textRangeToFile(problem.getHighlightRange()));
      for (QuickFix<?> fix : runner.toFixes(problem)) {
        annotation = annotation.withFix((IntentionAction)fix);
      }
      annotation.create();
    }
  }

}
