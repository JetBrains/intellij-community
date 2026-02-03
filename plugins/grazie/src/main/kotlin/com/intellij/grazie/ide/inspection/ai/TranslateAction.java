package com.intellij.grazie.ide.inspection.ai;

import ai.grazie.nlp.langs.Language;
import ai.grazie.nlp.langs.utils.NamesKt;
import ai.grazie.nlp.tokenizer.Tokenizer;
import ai.grazie.nlp.tokenizer.word.StandardWordTokenizer;
import com.intellij.codeInsight.intention.CustomizableIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.grazie.GrazieBundle;
import com.intellij.grazie.cloud.APIQueries;
import com.intellij.grazie.cloud.GrazieCloudConnector;
import com.intellij.grazie.cloud.PrematureEndException;
import com.intellij.grazie.detection.LangDetector;
import com.intellij.grazie.ide.fus.GrazieFUSCounter;
import com.intellij.grazie.ide.ui.PaddedListCellRenderer;
import com.intellij.grazie.rule.SentenceTokenizer;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.grazie.utils.NaturalTextDetector;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.ListSelectionModel;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.intellij.grazie.text.TextExtractor.findAllTextContents;

@SuppressWarnings("IntentionDescriptionNotFoundInspection")
public class TranslateAction implements IntentionAction, CustomizableIntentionAction {
  @Override
  public @IntentionName @NotNull String getText() {
    return GrazieBundle.message("intention.translate.text");
  }

  @Override
  public @NotNull @IntentionFamilyName String getFamilyName() {
    return getText();
  }

  @Override
  public @NotNull List<RangeToHighlight> getRangesToHighlight(@NotNull Editor editor, @NotNull PsiFile file) {
    int selStart = editor.getSelectionModel().getSelectionStart();
    int selEnd = editor.getSelectionModel().getSelectionEnd();
    List<RangeToTranslate> toTranslate = findAffectedRanges(file, selStart, selEnd);
    return ContainerUtil.map(toTranslate,
      p -> new RangeToHighlight(file, p.fileRange, EditorColors.SEARCH_RESULT_ATTRIBUTES));
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    SelectionModel model = editor.getSelectionModel();
    return !CommitMessage.isCommitMessage(psiFile) &&
           GrazieCloudConnector.Companion.seemsCloudConnected() &&
           !getAffectedTexts(psiFile, model.getSelectionStart(), model.getSelectionEnd()).isEmpty();
  }

  static class TranslationTarget {
    public String display;
    public Language language;

    TranslationTarget(Language language) {
      this.display = presentableLanguageName(language);
      this.language = language;
    }

    @Override
    public String toString() {
      return display;
    }
  }

  @VisibleForTesting
  public static String presentableLanguageName(Language language) {
    String nativeName = NamesKt.getNativeName(language);
    Font font = UIUtil.getLabelFont();
    return nativeName.codePoints().allMatch(font::canDisplay) ? nativeName : NamesKt.getEnglishName(language);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    int selStart = editor.getSelectionModel().getSelectionStart();
    int selEnd = editor.getSelectionModel().getSelectionEnd();

    List<RangeToTranslate> toTranslate = findAffectedRanges(psiFile, selStart, selEnd);

    if (toTranslate.isEmpty()) {
      GrazieFUSCounter.INSTANCE.reportTranslateError(Language.UNKNOWN, Language.UNKNOWN, 0);
      Messages.showErrorDialog(project, GrazieBundle.message("intention.translate.nothing.to.translate"), getText());
      return;
    }

    List<Language> detected = StreamEx.of(toTranslate)
      .map(pair -> LangDetector.INSTANCE.getLanguage(pair.contentFragment()))
      .filter(Objects::nonNull)
      .distinct()
      .toList();

    List<Language> targets = Arrays.stream(Language.values()).filter(l -> l != Language.UNKNOWN && !detected.contains(l)).toList();

    Language fromLang = detected.isEmpty() ? Language.UNKNOWN : detected.getFirst();

    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(ContainerUtil.map(targets, it -> new TranslationTarget(it)))
      .setRenderer(new PaddedListCellRenderer())
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setNamerForFiltering(t -> t.display + " " + NamesKt.getEnglishName(t.language))
      .setItemChosenCallback(toLang -> {
        GrazieFUSCounter.INSTANCE.reportTranslateRequested(fromLang, toLang.language, getWordCount(toTranslate));
        doTranslate(project, editor, psiFile, toTranslate, fromLang, toLang.language);
      })
      .createPopup()
      .showInBestPositionFor(editor);
  }

  private static List<RangeToTranslate> findAffectedRanges(PsiFile file, int selStart, int selEnd) {
    List<RangeToTranslate> texts = new ArrayList<>();
    List<TextContent> contents = getAffectedTexts(file, selStart, selEnd);
    for (TextContent content : contents) {
      int textStart = 0;
      int textEnd = content.length();
      for (var token : SentenceTokenizer.tokenize(content)) {
        int sentenceStart = CharArrayUtil.shiftForward(content, token.start(), " \t\n");
        int sentenceEnd = CharArrayUtil.shiftBackward(content, token.end() - 1, " \t\n") + 1;
        if (content.textOffsetToFile(sentenceEnd) < selStart) {
          textStart = sentenceEnd;
        }
        if (content.textOffsetToFile(sentenceStart) > selEnd && textEnd > sentenceStart) {
          textEnd = sentenceStart;
        }
      }
      textStart = CharArrayUtil.shiftForward(content, textStart, " \t\n");
      textEnd = CharArrayUtil.shiftBackward(content, textEnd - 1, " \t\n") + 1;
      if (textStart < textEnd) {
        TextRange textRange = new TextRange(textStart, textEnd);
        texts.add(new RangeToTranslate(content, textRange, content.textRangeToFile(textRange)));
      }
    }
    return texts;
  }

  @SuppressWarnings("UnstableApiUsage")
  private static void doTranslate(Project project, Editor editor, PsiFile file,
                                  List<RangeToTranslate> toTranslate,
                                  Language from, Language target
  ) {
    try {
      tryTranslate(project, editor, file, toTranslate, from, target);
    } catch (PrematureEndException e) {
      IdeUiService.getInstance().showErrorHint(editor, GrazieBundle.message("intention.translate.unavailable"));
    }
  }

  private static void tryTranslate(Project project, Editor editor, PsiFile file, List<RangeToTranslate> toTranslate,
                                   Language from,
                                   Language target) {
    String fileText = file.getViewProvider().getContents().toString();
    List<String> translations = ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(
        () -> APIQueries.getTranslator().translate(ContainerUtil.map(toTranslate, p -> p.fileRange.substring(fileText)), target, project),
        GrazieBundle.message("intention.translate.progress.title"), true, project
      );
    if (translations == null) {
      return;
    }
    if (translations.size() != toTranslate.size()) {
      throw new AssertionError("Translation server result mismatch");
    }
    Map<TextRange, String> replacements = new HashMap<>();
    for (int i = 0; i < translations.size(); i++) {
      replacements.put(toTranslate.get(i).fileRange, translations.get(i).strip());
    }

    int translationWordCount = replacements.values().stream().mapToInt(p -> words(p).size()).sum();

    WriteCommandAction.runWriteCommandAction(
      project, GrazieBundle.message("intention.translate.command.name", presentableLanguageName(target)), null, () -> {
        GrazieFUSCounter.INSTANCE.reportTranslateReplaced(from, target, getWordCount(toTranslate), translationWordCount);
        StreamEx.of(toTranslate)
          .map(p -> p.fileRange)
          .sorted(Segment.BY_START_OFFSET_THEN_END_OFFSET.reversed())
          .forEach(range ->
            editor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), replacements.get(range))
          );
      },
      file
    );
  }

  private static List<TextContent> getAffectedTexts(PsiFile file, int selStart, int selEnd) {
    if (selStart == selEnd) {
      TextContent textContent = TextExtractor.findTextAt(file, selStart, TextContent.TextDomain.ALL);
      if (textContent == null || !NaturalTextDetector.seemsNatural(textContent)) return Collections.emptyList();
      return List.of(textContent);
    }

    return ContainerUtil.filter(
      findAllTextContents(file.getViewProvider(), TextContent.TextDomain.ALL),
      tc -> tc.intersectsRange(new TextRange(selStart, selEnd))
    );
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static List<Tokenizer.Token> words(String text) {
    return StandardWordTokenizer.INSTANCE.tokenize(text).stream()
      .filter(token -> !token.getText().getValue().isBlank())
      .toList();
  }

  private static int getWordCount(List<RangeToTranslate> texts) {
    if (texts.isEmpty()) {
      return 0;
    }
    return texts.stream().mapToInt(p -> words(p.contentFragment()).size()).sum();
  }

  private record RangeToTranslate(TextContent content, TextRange contentRange, TextRange fileRange) {
    String contentFragment() {
      return contentRange.substring(content.toString());
    }
  }
}
