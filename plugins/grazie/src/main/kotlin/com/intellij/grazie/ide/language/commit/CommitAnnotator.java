package com.intellij.grazie.ide.language.commit;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.spellcheck.TypoProblem;
import com.intellij.grazie.text.CheckerRunner;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.grazie.text.TextProblem;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;

import static com.intellij.grazie.ide.TextProblemSeverities.GRAMMAR_ERROR;
import static com.intellij.grazie.ide.TextProblemSeverities.GRAMMAR_ERROR_ATTRIBUTES;
import static com.intellij.grazie.ide.TextProblemSeverities.STYLE_SUGGESTION;
import static com.intellij.grazie.ide.TextProblemSeverities.STYLE_SUGGESTION_ATTRIBUTES;
import static com.intellij.spellchecker.SpellCheckerSeveritiesProvider.TYPO;
import static com.intellij.spellchecker.SpellCheckerSeveritiesProvider.TYPO_KEY;

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
    CheckerRunner runner = new CheckerRunner(text);
    runner.run().forEach(problem -> {
      List<ProblemDescriptor> descriptors = runner.toProblemDescriptors(problem, true);
      if (descriptors.isEmpty()) return;

      String message = problem.getDescriptionTemplate(true);
      String tooltip = problem.getTooltipTemplate();
      LocalQuickFix[] fixes = runner.toFixes(problem, descriptors.getFirst());

      for (TextRange range : problem.getHighlightRanges()) {
        var severityAndAttributes = getSeverityAndAttributes(problem);
        AnnotationBuilder annotation = holder
          .newAnnotation(severityAndAttributes.getFirst(), message)
          .tooltip(tooltip)
          .textAttributes(severityAndAttributes.getSecond())
          .range(text.textRangeToFile(range));
        for (QuickFix<?> fix : fixes) {
          annotation = annotation.withFix((IntentionAction)fix);
        }
        annotation.create();
      }
    });
  }

  private static Pair<HighlightSeverity, TextAttributesKey> getSeverityAndAttributes(TextProblem problem) {
    if (problem.isStyleLike()) return Pair.create(STYLE_SUGGESTION, STYLE_SUGGESTION_ATTRIBUTES);
    else if (problem instanceof TypoProblem) return Pair.create(TYPO, TYPO_KEY);
    else return Pair.create(GRAMMAR_ERROR, GRAMMAR_ERROR_ATTRIBUTES);
  }
}
