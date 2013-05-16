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

public class StringValuePresenter implements XValuePresenter {
  public static final XValuePresenter DEFAULT = new StringValuePresenter(XValueNode.MAX_VALUE_LENGTH);

  private final int maxLength;

  public StringValuePresenter(int maxLength) {
    this.maxLength = maxLength;
  }

  @Override
  public void append(String value, SimpleColoredText text, boolean changed) {
    SimpleTextAttributes attributes = SimpleTextAttributes.fromTextAttributes(EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DefaultLanguageHighlighterColors.STRING));
    SimpleTextAttributes escapeAttributes = null;
    StringBuilder builder = new StringBuilder().append('"');
    for (int i = 0, n = Math.min(value.length(), maxLength); i < n; i++) {
      char ch = value.charAt(i);
      if (ch == '\\' || ch == '\"' || ch == '\b' || ch == '\t' || ch == '\n' || ch == '\f' || ch == '\r') {
        if (builder.length() > 0) {
          text.append(builder.toString(), attributes);
          builder.setLength(0);
        }

        if (escapeAttributes == null) {
          TextAttributes fromHighlighter = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE);
          if (fromHighlighter != null) {
            escapeAttributes = SimpleTextAttributes.fromTextAttributes(fromHighlighter);
          }
          else {
            escapeAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.GRAY);
          }
        }

        if (ch != '\\' && ch != '\"') {
          text.append("\\", escapeAttributes);
        }

        String string;
        switch (ch) {
          case '\b':
            string = "b";
            break;

          case '\t':
            string = "t";
            break;

          case '\n':
            string = "n";
            break;

          case '\f':
            string = "f";
            break;

          case '\r':
            string = "r";
            break;

          default:
            string = String.valueOf(ch);
            break;
        }
        text.append(string, escapeAttributes);
      }
      else {
        builder.append(ch);
      }
    }

    if (builder.length() > 0) {
      text.append(builder.append('"').toString(), attributes);
    }
  }
}