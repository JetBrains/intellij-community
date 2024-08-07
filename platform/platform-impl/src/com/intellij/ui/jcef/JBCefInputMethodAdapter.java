// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.ui.JBColor;
import com.intellij.util.ObjectUtils;
import org.cef.browser.CefBrowser;
import org.cef.input.CefCompositionUnderline;
import org.cef.misc.CefLog;
import org.cef.misc.CefRange;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.Arrays;
import java.util.List;

class JBCefInputMethodAdapter implements InputMethodRequests, InputMethodListener, JBCefCaretListener {
  // DEFAULT_RANGE represents an invalid range according to the chromium codebase.
  // It is used as none-value for optional arguments in CEF input methods API.
  public static final CefRange DEFAULT_RANGE = new CefRange(-1, -1);

  private CefBrowser myBrowser;
  private final JBCefOsrComponent myMyComponent;

  private volatile CefRange myCompositionSelectionRange;
  private volatile Rectangle[] myCompositionCharacterBounds;

  private volatile String mySelectedText = "";
  private volatile CefRange mySelectionRange = DEFAULT_RANGE;

  JBCefInputMethodAdapter(JBCefOsrComponent myComponent) { myMyComponent = myComponent; }

  void setBrowser(CefBrowser browser) {
    myBrowser = browser;
  }

  @Override
  public void inputMethodTextChanged(InputMethodEvent event) {
    CefLog.Debug("InputMethodListener::inputMethodTextChanged(event: %s)", event);

    int committedCharacterCount = event.getCommittedCharacterCount();

    AttributedCharacterIterator text = event.getText();
    if (text == null) {
      return;
    }
    char c = text.first();
    if (committedCharacterCount > 0) {
      StringBuilder textBuffer = new StringBuilder();
      while (committedCharacterCount-- > 0) {
        textBuffer.append(c);
        c = text.next();
      }

      String committedText = textBuffer.toString();
      CefRange replacementRange = DEFAULT_RANGE;
      int relativeCursorPos = 0;
      CefLog.Debug("CefBrowser::ImeCommitText(compositionText: '%s', replacementRange: %s, selectionRange: %s)", committedText,
                   replacementRange, relativeCursorPos);
      myBrowser.ImeCommitText(committedText, replacementRange, relativeCursorPos);

      // CEF doesn't notify about changing the selection range after committing. Current data is outdated. The selection range is unknown.
      mySelectedText = "";
      mySelectionRange = DEFAULT_RANGE;
    }

    StringBuilder textBuffer = new StringBuilder();
    while (c != CharacterIterator.DONE) {
      textBuffer.append(c);
      c = text.next();
    }

    var composedText = textBuffer.toString();
    if (!composedText.isEmpty()) {
      Color color = new JBColor(new Color(0, true), new Color(0, true));
      CefCompositionUnderline underline =
        new CefCompositionUnderline(new CefRange(0, composedText.length()), color, color, 0, CefCompositionUnderline.Style.SOLID);

      CefRange replacementRange = mySelectionRange;
      if (replacementRange == null || replacementRange.from == replacementRange.to) {
        // It has been experimentally determined that replacementRange must have an actual value only if there is a text to be replaced.
        // Passing zero-length ranges pointing to the caret position breaks Korean Language input character order.
        replacementRange = DEFAULT_RANGE;
      }

      // Selection range after replacement. Move the caret at the end of the composed text.
      CefRange selectionRange = new CefRange(composedText.length(), composedText.length());
      CefLog.Debug("CefBrowser::ImeSetComposition(compositionText: '%s', replacementRange: %s, selectionRange: %s)", composedText,
                   replacementRange, selectionRange);
      myBrowser.ImeSetComposition(composedText, List.of(underline), replacementRange, selectionRange);
    }
    event.consume();
  }

  @Override
  public void caretPositionChanged(InputMethodEvent event) {
    //
    CefLog.Debug("InputMethodListener::caretPositionChanged(event: %s)", event);
  }

  @Override
  public Rectangle getTextLocation(TextHitInfo offset) {
    Rectangle[] boxes =
      ObjectUtils.notNull(myCompositionCharacterBounds, new Rectangle[]{getDefaultImePositions()});
    Rectangle candidateWindowPosition = boxes.length == 0 ? getDefaultImePositions() : new Rectangle(boxes[0]);

    var componentLocation = myMyComponent.getLocationOnScreen();
    candidateWindowPosition.translate(componentLocation.x, componentLocation.y);
    CefLog.Debug("InputMethodRequests::getTextLocation(offset: %s) -> %s", offset, candidateWindowPosition);
    return candidateWindowPosition;
  }

  @Override
  public @Nullable TextHitInfo getLocationOffset(int x, int y) {
    Point p = new Point(x, y);
    var componentLocation = myMyComponent.getLocationOnScreen();
    p.translate(-componentLocation.x, -componentLocation.y);

    Rectangle[] boxes = ObjectUtils.notNull(myCompositionCharacterBounds, new Rectangle[0]);
    TextHitInfo result = null;
    for (int i = 0; i < boxes.length; i++) {
      Rectangle r = boxes[i];
      if (r.contains(p)) {
        result = TextHitInfo.leading(i);
        break;
      }
    }

    CefLog.Debug("InputMethodRequests::getTextLocation(x: %s, y: %s) -> %s", x, y, result);
    return result;
  }

  @Override
  public int getInsertPositionOffset() {
    // Not implemented. This isn't used by input methods for Chinese and Korean.
    CefLog.Debug("InputMethodRequests::getInsertPositionOffset() -> 0");
    return 0;
  }

  @Override
  public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex, AttributedCharacterIterator.Attribute[] attributes) {
    // It can't be supported. There no access to text in the input field in the browser
    CefLog.Debug("InputMethodRequests::getCommittedText(beginIndex: %s, endIndex: %s) -> ''", beginIndex, endIndex);
    return new AttributedString("").getIterator();
  }

  @Override
  public int getCommittedTextLength() {
    // It can't be supported. There no access to text in the input field in the browser
    CefLog.Debug("InputMethodRequests::getCommittedTextLength() -> 0");
    return 0;
  }

  @Override
  public @Nullable AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
    // Not implemented. This isn't used by input methods for Chinese and Korean.
    CefLog.Debug("InputMethodRequests::cancelLatestCommittedText(attributes: %s) -> 0", Arrays.toString(attributes));
    return null;
  }

  @Override
  public @Nullable AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
    CefLog.Debug("InputMethodRequests::getSelectedText(attributes: %s) -> 0", Arrays.toString(attributes));
    return new AttributedString(mySelectedText).getIterator();
  }

  private Rectangle getDefaultImePositions() {
    return new Rectangle(0, myMyComponent.getHeight(), 0, 0);
  }

  @Override
  public void onImeCompositionRangeChanged(CefRange selectionRange, Rectangle[] characterBounds) {
    myCompositionSelectionRange = selectionRange;
    myCompositionCharacterBounds = characterBounds;
    CefLog.Debug("CefRenderHandler::OnImeCompositionRangeChanged(selectionRange: '%s', characterBounds: %s)", mySelectionRange,
                 Arrays.toString(characterBounds));
  }

  @Override
  public void onTextSelectionChanged(String selectedText, CefRange selectionRange) {
    mySelectedText = selectedText;
    mySelectionRange = selectionRange;
    CefLog.Debug("CefRenderHandler::OnTextSelectionChanged(selectionText: '%s', selectionRange: %s)", mySelectedText, mySelectionRange);
  }
}
