/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.FontUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
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

    DefaultCaret caret = (DefaultCaret)getCaret();
    caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
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

  public void setBody(@NotNull String text) {
    if (text.isEmpty()) {
      setText("");
    }
    else {
      setText("<html><head>" +
              UIUtil.getCssFontDeclaration(getBodyFont()) +
              "</head><body>" +
              text +
              "</body></html>");
    }
  }

  @NotNull
  protected Font getBodyFont() {
    return FontUtil.getCommitMessageFont();
  }

  @NotNull
  protected abstract String getBody();

  @Override
  public void updateUI() {
    super.updateUI();
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
      String linkColor = "#" + ColorUtil.toHex(JBColor.link());
      styleSheet.addRule("a { color: " + linkColor + "; text-decoration: none;}");
    }
  }
}
