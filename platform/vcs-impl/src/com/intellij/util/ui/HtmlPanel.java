// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.FontUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.Position;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.IOException;
import java.io.StringWriter;

public abstract class HtmlPanel extends JEditorPane implements HyperlinkListener {
  public HtmlPanel() {
    super(UIUtil.HTML_MIME, "");
    setEditable(false);
    setOpaque(false);
    putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    addHyperlinkListener(this);
    setEditorKit(new HTMLEditorKitBuilder().withWordWrapViewFactory().build());
  }

  @Override
  public void hyperlinkUpdate(HyperlinkEvent e) {
    BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(e);
  }

  @Override
  public String getSelectedText() {
    Document doc = getDocument();
    int start = getSelectionStart();
    int end = getSelectionEnd();

    try {
      Position p0 = doc.createPosition(start);
      Position p1 = doc.createPosition(end);
      StringWriter sw = new StringWriter(p1.getOffset() - p0.getOffset());
      getEditorKit().write(sw, doc, p0.getOffset(), p1.getOffset() - p0.getOffset());

      return StringUtil.removeHtmlTags(sw.toString());
    }
    catch (BadLocationException | IOException ignored) {
    }
    return super.getSelectedText();
  }

  public void setBody(@NotNull @Nls String text) {
    if (text.isEmpty()) {
      setText("");
    }
    else {
      @NlsSafe String cssFontDeclaration = UIUtil.getCssFontDeclaration(getBodyFont());
      setText(new HtmlBuilder()
                .append(HtmlChunk.raw(cssFontDeclaration).wrapWith("head"))
                .append(HtmlChunk.raw(text).wrapWith(HtmlChunk.body()))
                .wrapWith(HtmlChunk.html()).toString());
    }
  }

  @NotNull
  protected Font getBodyFont() {
    return FontUtil.getCommitMessageFont();
  }

  @NotNull
  @Nls
  protected abstract String getBody();

  @Override
  public void updateUI() {
    super.updateUI();

    DefaultCaret caret = (DefaultCaret)getCaret();
    caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

    update();
  }

  public void update() {
    setBody(getBody());
    customizeLinksStyle();
    revalidate();
    repaint();
  }

  private void customizeLinksStyle() {
    Document document = getDocument();
    if (document instanceof HTMLDocument) {
      StyleSheet styleSheet = ((HTMLDocument)document).getStyleSheet();
      String linkColor = "#" + ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED); // NON-NLS
      styleSheet.addRule("a { color: " + linkColor + "; text-decoration: none;}"); // NON-NLS
    }
  }
}
