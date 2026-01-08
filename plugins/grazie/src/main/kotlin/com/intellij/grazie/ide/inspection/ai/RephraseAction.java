package com.intellij.grazie.ide.inspection.ai;

import ai.grazie.nlp.langs.Language;
import ai.grazie.nlp.tokenizer.word.StandardWordTokenizer;
import ai.grazie.rules.toolkit.LanguageToolkit;
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
import com.intellij.grazie.rule.ParsedSentence;
import com.intellij.grazie.rule.SentenceTokenizer;
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
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.grazie.utils.UtilsKt.ijRange;

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
    if (content == null || !NaturalTextDetector.seemsNatural(content)) return false;
    TextRange range = content.fileRangeToText(HighlightingUtil.selectionRange(editor));
    if (range == null) return false;
    if (LangDetector.INSTANCE.getLanguage(content.toString()) == null) return false;

    return ContainerUtil.exists(
      SentenceTokenizer.tokenize(content),
      sentence -> sentence.start() <= range.getStartOffset() && range.getEndOffset() <= sentence.end()
    );
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
          return rephraseTextContent(project, psiFile, selStart, selEnd);
        }

        int sentenceLength = sentence.text.length();
        TextRange textRange = fileRangeToText(selStart, selEnd, sentence);
        if (textRange == null) {
          return new SuggestionsWithLanguage(Language.UNKNOWN, Collections.emptyList(), sentenceLength, null, null);
        }

        Language iso = LangDetector.INSTANCE.getLanguage(sentence.text);
        if (iso == null) {
          return new SuggestionsWithLanguage(Language.UNKNOWN, Collections.emptyList(), sentenceLength, null, null);
        }

        String rangeText = textRange.subSequence(sentence.text).toString();
        int wordsRangeCount = StandardWordTokenizer.INSTANCE.words(rangeText).size();
        GrazieFUSCounter.INSTANCE.reportRephraseRequested(iso, sentence.text.length(), textRange.getLength(), wordsRangeCount);
        List<TextRange> ranges = getRangesToRephrase(sentence, textRange);
        List<Pair<TextRange, List<String>>> rephrasedSentences = rephrase(sentence.text, ranges, iso, project);
        if (rephrasedSentences.isEmpty()) {
          return new SuggestionsWithLanguage(iso, Collections.emptyList(), sentenceLength, textRange.getLength(), wordsRangeCount);
        }

        List<ListItem> suggestions = ContainerUtil.flatMap(rephrasedSentences, s -> toListItem(sentence, s));
        return new SuggestionsWithLanguage(iso, suggestions, sentenceLength, textRange.getLength(), wordsRangeCount);
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

  private static @NotNull SuggestionsWithLanguage rephraseTextContent(@NotNull Project project, PsiFile psiFile, int selStart, int selEnd) {
    TextContent text = TextExtractor.findTextAt(psiFile, selStart, TextContent.TextDomain.ALL);
    if (text == null || text.toString().isBlank()) {
      return new SuggestionsWithLanguage(Language.UNKNOWN, Collections.emptyList(), null, null, null);
    }

    Language iso = LangDetector.INSTANCE.getLanguage(text.toString());
    if (iso == null) {
      return new SuggestionsWithLanguage(Language.UNKNOWN, Collections.emptyList(), text.length(), null, null);
    }

    var textRange = selStart == selEnd ? TextRange.allOf(text.toString()) : text.fileRangeToText(TextRange.create(selStart, selEnd));
    if (textRange == null) {
      return new SuggestionsWithLanguage(iso, Collections.emptyList(), text.length(), null, null);
    }

    var rephrasedSentences = rephrase(text.toString(), List.of(textRange), iso, project);
    int wordsRangeCount = StandardWordTokenizer.INSTANCE.words(text.toString()).size();
    if (rephrasedSentences.isEmpty()) {
      return new SuggestionsWithLanguage(iso, Collections.emptyList(), text.length(), textRange.getLength(), wordsRangeCount);
    }

    List<ListItem> suggestions = ContainerUtil.flatMap(rephrasedSentences, s -> toListItem(text, s));
    return new SuggestionsWithLanguage(iso, suggestions, text.length(), textRange.getLength(), wordsRangeCount);
  }

  private static @NotNull List<ListItem> toListItem(TextContent content, Pair<TextRange, List<String>> rephrasedSentences) {
    TextRange range = rephrasedSentences.getFirst();
    TextRange fileRange = new TextRange(content.textOffsetToFile(range.getStartOffset()), content.textOffsetToFile(range.getEndOffset()));
    return ContainerUtil.map(rephrasedSentences.getSecond(), item -> new ListItem(fileRange, item));
  }

  private static @NotNull List<ListItem> toListItem(ParsedSentence sentence, Pair<TextRange, List<String>> rephrasedSentences) {
    TextRange range = rephrasedSentences.getFirst();
    TextRange fileRange = new TextRange(sentence.textOffsetToFile(range.getStartOffset()), sentence.textOffsetToFile(range.getEndOffset()));
    return ContainerUtil.map(rephrasedSentences.getSecond(), item -> new ListItem(fileRange, item));
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
          highlighter.set(editor.getMarkupModel().addRangeHighlighter(
            EditorColors.SEARCH_RESULT_ATTRIBUTES,
            item.fileRange.getStartOffset(), item.fileRange.getEndOffset(),
            HighlighterLayer.SELECTION + 1, HighlighterTargetArea.EXACT_RANGE
          ));
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

  private static @Nullable TextRange fileRangeToText(int selStart, int selEnd, ParsedSentence sentence) {
    Integer textStart = sentence.fileOffsetToText(selStart);
    Integer textEnd = sentence.fileOffsetToText(selEnd);
    return textStart == null || textEnd == null ? null : new TextRange(textStart, textEnd);
  }

  private static List<TextRange> getRangesToRephrase(ParsedSentence sentence, TextRange textRange) {
    if (textRange.getLength() > 0) {
      return List.of(Text.alignToWordBounds(textRange, sentence.text));
    }

    var toolkit = LanguageToolkit.forLanguage(sentence.tree.treeSupport().getGrazieLanguage());
    var extendRanges = toolkit.selectioner().calcExtendSelectionRanges(sentence.tree, textRange.getStartOffset(), textRange.getEndOffset());
    assert !extendRanges.isEmpty();
    List<TextRange> result = new ArrayList<>();
    result.add(ijRange(extendRanges.getFirst()));
    for (int i = 1; i < extendRanges.size(); i++) {
      var range = extendRanges.get(i);
      if (Strings.countChars(sentence.text.substring(range.start(), range.end()), ' ') > 10) {
        break;
      }
      result.add(ijRange(range));
    }
    return result;
  }

  private static @NotNull List<Pair<TextRange, List<String>>> rephrase(String content, List<TextRange> ranges, Language iso, Project project) {
    try {
      List<Pair<TextRange, List<String>>> rephrased = APIQueries.getRephraser().rephrase(content, ranges, iso, project);
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
