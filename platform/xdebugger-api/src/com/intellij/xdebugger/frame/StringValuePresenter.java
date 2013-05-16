/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.xdebugger.frame;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

public class StringValuePresenter implements XValuePresenter {
  public static final XValuePresenter DEFAULT = new StringValuePresenter(XValueNode.MAX_VALUE_LENGTH, "\"\\");

  private final int maxLength;
  private final String additionalChars;

  public StringValuePresenter(int maxLength, @Nullable String additionalChars) {
    this.maxLength = maxLength;
    this.additionalChars = additionalChars;
  }

  @Override
  public void append(String value, SimpleColoredText text, boolean changed) {
    SimpleTextAttributes attributes = SimpleTextAttributes.fromTextAttributes(EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DefaultLanguageHighlighterColors.STRING));
    text.append("\"", attributes);
    doAppend(value, text, attributes);
    text.append("\"", attributes);
  }

  protected void doAppend(String value, SimpleColoredText text, SimpleTextAttributes attributes) {
    SimpleTextAttributes escapeAttributes = null;
    int lastOffset = 0;
    int length = maxLength == -1 ? value.length() : Math.min(value.length(), maxLength);
    for (int i = 0; i < length; i++) {
      char ch = value.charAt(i);
      int additionalCharIndex = -1;
      if (ch == '\n' || ch == '\r' || ch == '\t' || ch == '\b' || ch == '\f' || (additionalChars != null && (additionalCharIndex = additionalChars.indexOf(ch)) != -1)) {
        if (i > lastOffset) {
          text.append(value.substring(lastOffset, i), attributes);
        }
        lastOffset = i + 1;

        if (escapeAttributes == null) {
          TextAttributes fromHighlighter = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE);
          if (fromHighlighter != null) {
            escapeAttributes = SimpleTextAttributes.fromTextAttributes(fromHighlighter);
          }
          else {
            escapeAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.GRAY);
          }
        }

        if (additionalCharIndex == -1) {
          text.append("\\", escapeAttributes);
        }

        String string;
        switch (ch) {
          case '"':
            string = "\"";
            break;

          case '\\':
            string = "\\";
            break;

          case '\n':
            string = "n";
            break;

          case '\r':
            string = "r";
            break;

          case '\t':
            string = "t";
            break;

          case '\b':
            string = "b";
            break;

          case '\f':
            string = "f";
            break;

          default:
            string = String.valueOf(ch);
        }
        text.append(string, escapeAttributes);
      }
    }

    if (lastOffset < length) {
      text.append(value.substring(lastOffset, length), attributes);
    }
  }
}