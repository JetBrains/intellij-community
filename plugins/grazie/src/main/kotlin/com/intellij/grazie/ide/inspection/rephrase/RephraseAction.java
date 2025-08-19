package com.intellij.grazie.ide.inspection.rephrase;

import ai.grazie.nlp.langs.Language;
import ai.grazie.nlp.tokenizer.word.WhitespaceWordTokenizer;
import ai.grazie.rules.toolkit.LanguageToolkit;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.grazie.GrazieBundle;
import com.intellij.grazie.cloud.GrazieCloudConnector;
import com.intellij.grazie.detection.LangDetector;
import com.intellij.grazie.ide.fus.GrazieFUSCounter;
import com.intellij.grazie.ide.ui.PaddedListCellRenderer;
import com.intellij.grazie.rule.ParsedSentence;
import com.intellij.grazie.rule.SentenceTokenizer;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.grazie.utils.HighlightingUtil;
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
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.grazie.utils.HighlightingUtil.toIdeaRange;

@SuppressWarnings("IntentionDescriptionNotFoundInspection")
public class RephraseAction extends IntentionAndQuickFixAction {
  private static final Set<Language> SUPPORTED_LANGUAGES = Set.of(Language.ENGLISH);

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

    TextContent content =
      TextExtractor.findTextAt(psiFile, editor.getCaretModel().getOffset(), TextContent.TextDomain.ALL);
    if (content == null) return false;

    TextRange range = content.fileRangeToText(HighlightingUtil.selectionRange(editor));
    if (range == null) return false;

    Language language = LangDetector.INSTANCE.getLanguage(content.toString());
    if (language == null || !SUPPORTED_LANGUAGES.contains(language)) return false;

    return SentenceTokenizer.tokenize(content).stream().anyMatch(t ->
      t.start() <= range.getStartOffset() && range.getEndOffset() <= t.end());
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

    int selStart = editor.getSelectionModel().getSelectionStart();
    int selEnd = editor.getSelectionModel().getSelectionEnd();

    SuggestionsWithLanguage rephraseData = ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(() -> ReadAction.compute(() -> {
        ParsedSentence sentence = ParsedSentence.findSentenceInFile(psiFile, selStart);
        if (sentence == null) {
          return new SuggestionsWithLanguage(Language.UNKNOWN, Collections.emptyList(), null, null, null);
        }

        Integer sentenceLength = sentence.text.length();
        TextRange textRange = fileRangeToText(selStart, selEnd, sentence);
        if (textRange == null)
          return new SuggestionsWithLanguage(Language.UNKNOWN, Collections.emptyList(), sentenceLength, null,
            null);

        Language iso = LangDetector.INSTANCE.getLanguage(sentence.text);
        if (iso == null)
          return new SuggestionsWithLanguage(Language.UNKNOWN, Collections.emptyList(), sentenceLength, null,
            null);

        String rangeText = textRange.subSequence(sentence.text).toString();
        int wordsRangeCount = WhitespaceWordTokenizer.INSTANCE.words(rangeText).size();
        int rangeLength = textRange.getLength();
        GrazieFUSCounter.INSTANCE.reportRephraseRequested(iso, sentence.text.length(), rangeLength, wordsRangeCount);
        List<TextRange> ranges = getRangesToRephrase(sentence, textRange);
        List<String> result = GrazieCloudConnector.Companion.getEP_NAME().getExtensionList()
          .stream()
          .map(connector -> connector.rephrase(sentence.text, ranges, iso, project))
          .filter(Objects::nonNull)
          .findFirst()
          .orElse(null);
        if (result == null)
          return new SuggestionsWithLanguage(iso, Collections.emptyList(), sentenceLength, rangeLength, wordsRangeCount);

        TextRange minRange = Text.alignToWordBounds(textRange, sentence.text);
        List<ListItem> suggestions = ContainerUtil.mapNotNull(result,
          s -> toListItem(minRange, sentence, sentence.text, s));
        return new SuggestionsWithLanguage(iso, suggestions, sentenceLength, rangeLength, wordsRangeCount);
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

  private static @Nullable TextRange fileRangeToText(int selStart, int selEnd, ParsedSentence sentence) {
    Integer textStart = sentence.fileOffsetToText(selStart);
    Integer textEnd = sentence.fileOffsetToText(selEnd);
    return textStart == null || textEnd == null ? null : new TextRange(textStart, textEnd);
  }

  private static ListItem toListItem(TextRange minRange, ParsedSentence sentence, String sentenceText,
                                     String suggestion) {
    int commonPrefix = StringUtil.commonPrefix(sentenceText, suggestion).length();
    int commonSuffix = StringUtil.commonSuffix(sentenceText, suggestion.substring(commonPrefix)).length();
    if (commonPrefix > minRange.getEndOffset() + 1 ||
        sentenceText.length() - commonSuffix < minRange.getStartOffset() - 1) {
      return null;
    }

    TextRange range = Text.alignToWordBounds(
      new TextRange(
        Math.min(minRange.getStartOffset(), commonPrefix),
        Math.max(sentenceText.length() - commonSuffix, minRange.getEndOffset())
      ),
      sentenceText
    );
    String replacement = suggestion.substring(range.getStartOffset(), suggestion.length() - (sentenceText.length() - range.getEndOffset()));
    TextRange fileRange = new TextRange(sentence.textOffsetToFile(range.getStartOffset()), sentence.textOffsetToFile(range.getEndOffset()));
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
        int rephraseWordCount = WhitespaceWordTokenizer.INSTANCE.words(item.replacement).size();
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

  private static List<TextRange> getRangesToRephrase(ParsedSentence sentence, TextRange textRange) {
    if (textRange.getLength() > 0) {
      return List.of(Text.alignToWordBounds(textRange, sentence.text));
    }

    var toolkit = LanguageToolkit.forLanguage(sentence.tree.treeSupport().getGrazieLanguage());
    var extendRanges = toolkit.selectioner()
      .calcExtendSelectionRanges(sentence.tree, textRange.getStartOffset(), textRange.getEndOffset());
    assert !extendRanges.isEmpty();
    List<TextRange> result = new ArrayList<>();
    result.add(toIdeaRange(extendRanges.get(0)));
    for (int i = 1; i < extendRanges.size(); i++) {
      var range = extendRanges.get(i);
      if (Strings.countChars(sentence.text.substring(range.start(), range.end()), ' ') > 10) {
        break;
      }
      result.add(toIdeaRange(range));
    }
    return result;
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
