package com.intellij.grazie.ide.inspection.ai;

import ai.grazie.nlp.langs.Language;
import ai.grazie.nlp.tokenizer.word.StandardWordTokenizer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.grazie.GrazieBundle;
import com.intellij.grazie.cloud.APIQueries;
import com.intellij.grazie.cloud.GrazieCloudConnector;
import com.intellij.grazie.cloud.TaskServerException;
import com.intellij.grazie.detection.LangDetector;
import com.intellij.grazie.ide.fus.GrazieFUSCounter;
import com.intellij.grazie.ide.ui.PaddedListCellRenderer;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.grazie.utils.HighlightingUtil;
import com.intellij.grazie.utils.NaturalTextDetector;
import com.intellij.grazie.utils.Text;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("IntentionDescriptionNotFoundInspection")
public class RephraseAction extends IntentionAndQuickFixAction {

  @Override
  public @IntentionName @NotNull String getName() {
    return GrazieBundle.message("intention.rephrase.text");
  }

  @Override
  public @NotNull @IntentionFamilyName String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (editor == null || CommitMessage.isCommitMessage(psiFile)) return false;
    if (!GrazieCloudConnector.Companion.seemsCloudConnected()) {
      return false;
    }

    TextContent content = TextExtractor.findTextAt(psiFile, editor.getCaretModel().getOffset(), TextContent.TextDomain.ALL);
    TextRange range = HighlightingUtil.selectionRange(editor);
    if (content == null || (range.isEmpty() && !NaturalTextDetector.seemsNatural(content))) return false;
    return content.fileRangeToText(range) != null;
  }

  public record SuggestionsWithLanguage(
    @NotNull Language language,
    @NotNull List<ListItem> suggestions,
    @Nullable Integer textLength,
    @Nullable Integer rangeLength,
    @Nullable Integer wordRangeCount
  ) {
  }

  @Override
  public void applyFix(@NotNull Project project, PsiFile psiFile, @Nullable Editor editor) {
    if (editor == null) return;

    SuggestionsWithLanguage rephraseData = ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(() -> ReadAction.compute(() -> {
        TextContent content = TextExtractor.findTextAt(psiFile, editor.getSelectionModel().getSelectionStart(), TextContent.TextDomain.ALL);
        if (content == null) {
          return new SuggestionsWithLanguage(Language.UNKNOWN, Collections.emptyList(), null, null, null);
        }

        TextRange textRange = content.fileRangeToText(HighlightingUtil.selectionRange(editor));
        if (textRange == null) {
          return new SuggestionsWithLanguage(Language.UNKNOWN, Collections.emptyList(), content.length(), null, null);
        }

        Language iso = LangDetector.INSTANCE.getLanguage(content.toString());
        if (iso == null) {
          return new SuggestionsWithLanguage(Language.UNKNOWN, Collections.emptyList(), content.length(), null, null);
        }

        int wordRangeCount = StandardWordTokenizer.INSTANCE.words(content.toString()).size();
        int rangeLength = textRange.getLength();
        GrazieFUSCounter.INSTANCE.reportRephraseRequested(iso, content.length(), rangeLength, wordRangeCount);
        TextRange wordBoundRange = Text.alignToWordBounds(textRange, content.toString());
        List<String> rephrasedSentences = rephrase(content, wordBoundRange, iso, project);
        if (rephrasedSentences.isEmpty()) {
          return new SuggestionsWithLanguage(iso, Collections.emptyList(), content.length(), rangeLength, wordRangeCount);
        }

        List<ListItem> suggestions = ContainerUtil.mapNotNull(
          rephrasedSentences, rephrasedSentence -> toListItem(wordBoundRange, content, rephrasedSentence)
        );
        return new SuggestionsWithLanguage(iso, suggestions, content.length(), rangeLength, wordRangeCount);
      }), GrazieBundle.message("intention.rephrase.progress.title"), true, project);

    if (rephraseData.suggestions().isEmpty()) {
      GrazieFUSCounter.INSTANCE.reportRephraseEmpty(
        rephraseData.language, rephraseData.textLength, rephraseData.rangeLength, rephraseData.wordRangeCount
      );
      HintManager.getInstance().showErrorHint(editor, GrazieBundle.message("intention.rephrase.no.results.popup"));
      return;
    }

    showPopup(project, editor, psiFile, rephraseData);
  }

  private static ListItem toListItem(TextRange minRange, TextContent content, String suggestion) {
    int commonPrefix = StringUtil.commonPrefixLength(content.toString(), suggestion);
    int commonSuffix = StringUtil.commonSuffixLength(content.toString(), suggestion.substring(commonPrefix));

    if (commonPrefix == 0 && commonSuffix == 0) {
      return new ListItem(content.textRangeToFile(minRange), suggestion);
    }

    if (commonPrefix > minRange.getEndOffset() + 1 || content.length() - commonSuffix < minRange.getStartOffset() - 1) {
      return null;
    }

    TextRange range = Text.alignToWordBounds(
      new TextRange(
        Math.min(minRange.getStartOffset(), commonPrefix),
        Math.max(content.length() - commonSuffix, minRange.getEndOffset())
      ),
      content
    );
    String replacement = suggestion.substring(range.getStartOffset(), suggestion.length() - (content.length() - range.getEndOffset()));
    TextRange fileRange = new TextRange(content.textOffsetToFile(range.getStartOffset()), content.textOffsetToFile(range.getEndOffset()));
    return new ListItem(fileRange, replacement);
  }

  private void showPopup(Project project, Editor editor, PsiFile file, SuggestionsWithLanguage descriptor) {
    Ref<RangeHighlighter> highlighter = new Ref<>();

    List<ListItem> suggestions = descriptor.suggestions();

    var popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(suggestions)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setRenderer(new PaddedListCellRenderer())
      .setItemChosenCallback(item -> WriteCommandAction.runWriteCommandAction(project, getName(), null, () -> {
        int selectedRank = suggestions.indexOf(item);
        editor.getDocument().replaceString(item.fileRange.getStartOffset(), item.fileRange.getEndOffset(), item.replacement);
        int rephraseLength = item.replacement.length();
        int rephraseWordCount = StandardWordTokenizer.INSTANCE.words(item.replacement).size();
        GrazieFUSCounter.INSTANCE.reportRephraseApplied(
          descriptor.language, suggestions.size(), rephraseLength, rephraseWordCount, selectedRank
        );
      }, file))
      .setNamerForFiltering(i -> i.replacement)
      .setItemSelectedCallback(item -> {
        dropHighlighter(highlighter);
        if (item != null) {
          highlighter.set(editor.getMarkupModel()
            .addRangeHighlighter(EditorColors.SEARCH_RESULT_ATTRIBUTES, item.fileRange.getStartOffset(), item.fileRange.getEndOffset(),
              HighlighterLayer.SELECTION + 1, HighlighterTargetArea.EXACT_RANGE));
        }
      })
      .addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          dropHighlighter(highlighter);
          if (!event.isOk()) {
            GrazieFUSCounter.INSTANCE.reportRephraseRejected(descriptor.language(), suggestions.size());
          }
        }
      })
      .createPopup();

    popup.showInBestPositionFor(editor);
  }

  private static void dropHighlighter(Ref<RangeHighlighter> highlighter) {
    RangeHighlighter rh = highlighter.get();
    if (rh != null) {
      rh.dispose();
    }
    highlighter.set(null);
  }

  private static @NotNull List<String> rephrase(TextContent content, TextRange wordBoundRange, Language iso, Project project) {
    try {
      List<String> rephrased = APIQueries.getRephraser().rephrase(content.toString(), wordBoundRange, iso, project);
      return rephrased == null ? Collections.emptyList() : rephrased;
    } catch (TaskServerException e) {
      return Collections.emptyList();
    }
  }

  private record ListItem(TextRange fileRange, String replacement) {
      @Override
      public String toString() {
        return replacement;
      }
    }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
