package com.intellij.grazie.ide.inspection.ai;

import ai.grazie.def.WordDefinition;
import ai.grazie.nlp.langs.Language;
import ai.grazie.nlp.tokenizer.word.WhitespaceWordTokenizer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.grazie.GrazieBundle;
import com.intellij.grazie.cloud.APIQueries;
import com.intellij.grazie.cloud.GrazieCloudConnector;
import com.intellij.grazie.detection.LangDetector;
import com.intellij.grazie.ide.fus.GrazieFUSCounter;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.grazie.utils.HighlightingUtil;
import com.intellij.grazie.utils.NaturalTextDetector;
import com.intellij.grazie.utils.Text;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.IconUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.SwingHelper;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;

import static com.intellij.grazie.utils.GrazieUtilsKt.getLanguageIfAvailable;

@SuppressWarnings("IntentionDescriptionNotFoundInspection")
public class WordDefinitions implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(WordDefinitions.class);

  @Override
  public @IntentionName @NotNull String getText() {
    return GrazieBundle.message("action.intention.word.definition.text");
  }

  @Override
  public @NotNull @IntentionFamilyName String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (CommitMessage.isCommitMessage(psiFile) || !GrazieCloudConnector.Companion.seemsCloudConnected()) return false;

    TextContent content =
      TextExtractor.findTextAt(psiFile, editor.getCaretModel().getOffset(), TextContent.TextDomain.ALL);
    TextRange selectionRange = HighlightingUtil.selectionRange(editor);
    if (content == null || (selectionRange.isEmpty() && !NaturalTextDetector.seemsNatural(content))) return false;

    TextRange range = textRangeToQuery(content, selectionRange);
    if (!(range != null && !range.isEmpty())) return false;

    Language language = getLanguageIfAvailable(content.toString());
    return language != null && APIQueries.defLanguages.contains(language);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    WordDefinitionWithLanguage descriptor = findDefinitions(editor, psiFile);
    if (descriptor == null) return;

    showPopup(editor, descriptor);
  }

  public record WordDefinitionWithLanguage(
    @NotNull WordDefinition definition,
    @NotNull Language language
  ) {}

  public static WordDefinitionWithLanguage findDefinitions(Editor editor, PsiFile file) {
    TextContent content =
      TextExtractor.findTextAt(file, editor.getCaretModel().getOffset(), TextContent.TextDomain.ALL);
    if (content == null) return null;

    TextRange textRange = textRangeToQuery(content, HighlightingUtil.selectionRange(editor));
    Language lang = LangDetector.INSTANCE.getLanguage(content.toString());
    if (lang == null || textRange == null || textRange.isEmpty()) return null;

    String rangeText = textRange.subSequence(content).toString();
    int wordsCount = WhitespaceWordTokenizer.INSTANCE.words(rangeText).size();
    GrazieFUSCounter.INSTANCE.reportDefinitionRequested(lang, wordsCount);
    WordDefinition descriptor = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> APIQueries.definitions(content.toString(), textRange, lang, file.getProject()),
      GrazieBundle.message("action.definition.progress.title", rangeText), true, file.getProject()
    );
    if (descriptor == null) {
      Messages.showInfoMessage(
        file.getProject(),
        GrazieBundle.message("action.definition.not.found.message", rangeText),
        GrazieBundle.message("action.definition.not.found.title")
      );
      return null;
    }
    return new WordDefinitionWithLanguage(descriptor, lang);
  }

  private static void showPopup(Editor editor, WordDefinitionWithLanguage descriptor) {
    JEditorPane html = SwingHelper.createHtmlViewer(true, null, null, null);

    FontMetrics metrics = html.getFontMetrics(html.getFont());
    int limit = metrics.stringWidth("w".repeat(30));
    int border = metrics.stringWidth("J");

    var definition = descriptor.definition();
    html.setText(renderResult(definition));
    html.setBorder(new JBEmptyBorder(border));
    html.setSize(limit + border * 2, limit + border * 2);
    int preferredHeight = html.getPreferredSize().height;

    Icon icon = downloadImage(editor, definition, limit);
    boolean iconNorth = icon.getIconWidth() > icon.getIconHeight();

    JPanel component = new JPanel(new BorderLayout());
    component.add(ScrollPaneFactory.createScrollPane(html), BorderLayout.CENTER);
    component.add(new JLabel(icon), iconNorth ? BorderLayout.NORTH : BorderLayout.EAST);
    Dimension componentSize = new Dimension(
      Math.max(html.getWidth(), limit) + (iconNorth ? 0 : icon.getIconWidth()) + border * 2,
      preferredHeight + (iconNorth ? icon.getIconHeight() : 0) + border * 2);
    component.setSize(componentSize);

    var popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(component, null)
      .setCancelOnClickOutside(true)
      .setCancelOnOtherWindowOpen(true)
      .setResizable(true)
      .setMovable(true)
      .setMinSize(componentSize)
      .createPopup();
    popup.addListener(new JBPopupListener() {
      private long shownAt = 0;

      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
        shownAt = System.currentTimeMillis();
      }

      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        var wasShownFor = System.currentTimeMillis() - shownAt;
        GrazieFUSCounter.INSTANCE.reportDefinitionShown(
          descriptor.language(),
          descriptor.definition().getDefinitions().length,
          Duration.ofMillis(wasShownFor)
        );
      }
    });
    popup.showInBestPositionFor(editor);
  }

  private static Icon downloadImage(Editor editor, WordDefinition descriptor, int limit) {
    if (descriptor.getImage_link() != null && descriptor.getDefinitions().length == 1) {
      try {
        Icon icon = new ImageIcon(ImageIO.read(new URL(descriptor.getImage_link())));
        if (icon.getIconWidth() > limit || icon.getIconHeight() > limit) {
          int maxDimension = Math.max(icon.getIconWidth(), icon.getIconHeight());
          return IconUtil.scale(icon, editor.getComponent(), ((float) limit) / maxDimension);
        }
        return icon;
      } catch (IOException ex) {
        LOG.info(ex);
      }
    }
    return EmptyIcon.create(0);
  }

  private static TextRange textRangeToQuery(TextContent content, TextRange range) {
    TextRange textRange = content.fileRangeToText(range);
    return textRange == null ? null : Text.alignToWordBounds(textRange, content);
  }

  private static String renderResult(WordDefinition descriptor) {
    String prefix = "<b>" + descriptor.getWord() + "</b> ";
    if (descriptor.getTranscription() != null) prefix += "<i>" + descriptor.getTranscription() + "</i> ";
    if (descriptor.getPos() != null) prefix += "<i>" + descriptor.getPos() + "</i> ";
    if (descriptor.getDefinitions().length == 1) {
      return prefix + "<br><br>" + renderDefinition(descriptor.getDefinitions()[0]);
    }

    StringBuilder result = new StringBuilder(prefix + "<ol>");
    for (var definition : descriptor.getDefinitions()) {
      result.append("<li>").append(renderDefinition(definition));
    }
    result.append("</ol>");
    return result.toString();
  }


  private static String renderDefinition(WordDefinition.Definition definition) {
    String result = "";
    if (definition.getTopic() != null) result += "<i>" + definition.getTopic() + "</i> ";
    if (definition.getTags() != null) result += "<i>" + String.join(", ", definition.getTags()) + "</i> ";
    result += definition.getDefinition() + " ";
    if (definition.getContent_link() != null)
      result += "<a href=\"" + definition.getContent_link() + "\">Wikipedia</a>";
    return result.trim();
  }
}
