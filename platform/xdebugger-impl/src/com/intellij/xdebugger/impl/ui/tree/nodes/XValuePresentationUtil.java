// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class XValuePresentationUtil {
  public static void renderValue(@NotNull @NlsSafe String value,
                                 @NotNull ColoredTextContainer text,
                                 @NotNull SimpleTextAttributes attributes,
                                 int maxLength,
                                 @Nullable String additionalSpecialCharsToHighlight) {
    renderValue(value, text, attributes, maxLength, additionalSpecialCharsToHighlight,
                DebuggerUIUtil.getColorScheme().getAttributes(DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE));
  }

  @ApiStatus.Internal
  public static void renderValue(@NotNull @NlsSafe String value,
                                 @NotNull ColoredTextContainer text,
                                 @NotNull SimpleTextAttributes attributes,
                                 int maxLength,
                                 @Nullable String additionalSpecialCharsToHighlight,
                                 @Nullable TextAttributes escapeCharsAttribute) {
    SimpleTextAttributes escapeAttributes;
    if (escapeCharsAttribute != null) {
      escapeAttributes = SimpleTextAttributes.fromTextAttributes(escapeCharsAttribute);
    }
    else {
      escapeAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.GRAY);
    }

    int lastOffset = 0;
    int length = maxLength == -1 ? value.length() : Math.min(value.length(), maxLength);
    for (int i = 0; i < length; i++) {
      char ch = value.charAt(i);
      if (isEscapingSymbol(ch)
          || (additionalSpecialCharsToHighlight != null && additionalSpecialCharsToHighlight.indexOf(ch) != -1)) {
        if (i > lastOffset) {
          text.append(value.substring(lastOffset, i), attributes);
        }
        lastOffset = i + 1;

        if (isEscapingSymbol(ch)) {
          text.append("\\", escapeAttributes);
        }

        text.append(String.valueOf(getEscapingSymbol(ch)), escapeAttributes);
      }
    }

    if (lastOffset < length) {
      text.append(value.substring(lastOffset, length), attributes);
    }
  }

  private static boolean isEscapingSymbol(char ch) {
    return getEscapingSymbol(ch) != ch;
  }

  @ApiStatus.Experimental
  public static void renderName(@NotNull @NlsSafe String name,
                                int maxOutputLength,
                                Consumer<@NlsSafe String> appender) {
    int lastPartStart = 0;
    int lastPartEnd = 0;
    boolean lastCharWasWhitespace = false;
    int appendedCount = 0;
    for (int i = 0; i < name.length(); i++) {
      char ch = name.charAt(i);
      if (isEscapingSymbol(ch) || ch == ' ') {
        lastCharWasWhitespace = true;
      }
      else {
        if (lastCharWasWhitespace) {
          appendedCount += appendPart(name, appender, maxOutputLength, appendedCount, lastPartStart, lastPartEnd);
          if (appendedCount > 0 && appendedCount < maxOutputLength) {
            appendedCount++;
            appender.accept(" ");
          }
          if (appendedCount == maxOutputLength) {
            return;
          }
          lastPartStart = i;
          lastPartEnd = i + 1;
        }
        else {
          lastPartEnd++;
        }
        lastCharWasWhitespace = false;
      }
    }

    if (lastPartStart < lastPartEnd) {
      appendPart(name, appender, maxOutputLength, appendedCount, lastPartStart, lastPartEnd);
    }
  }

  private static int appendPart(@NotNull String name,
                                @NotNull Consumer<String> appender,
                                int maxOutputLength,
                                int appendedCount,
                                int lastPartStart,
                                int lastPartEnd) {
    int remainingLength = maxOutputLength - appendedCount - (lastPartEnd - lastPartStart);
    if (remainingLength <= 0) {
      lastPartEnd = Math.max(lastPartStart, lastPartEnd + remainingLength);
    }
    String substring = name.substring(lastPartStart, lastPartEnd);
    appender.accept(substring);
    return substring.length();
  }


  private static char getEscapingSymbol(char ch) {
    return switch (ch) {
      case '\n' -> 'n';
      case '\r' -> 'r';
      case '\t' -> 't';
      case '\b' -> 'b';
      case '\f' -> 'f';
      // Java doesn't support two more standard escape symbols \a & \v, but many other languages support them.
      // So we print them nicely for all languages and hope that it should not negatively affect any language.
      case 0x07 -> 'a';
      case 0x0b -> 'v';
      default -> ch;
    };
  }

  public static void appendSeparator(@NotNull ColoredTextContainer text, @NotNull @NlsSafe String separator) {
    if (!separator.isEmpty()) {
      text.append(separator, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  public static @NotNull String computeValueText(@NotNull XValuePresentation presentation) {
    XValuePresentationTextExtractor extractor = new XValuePresentationTextExtractor();
    presentation.renderValue(extractor);
    return extractor.getText();
  }

  /**
   * Tells whether the given renderer is supposed to extract a plain text presentation of the value,
   * which is used by the "Copy Value" action, for instance.
   */
  public static boolean isValueTextExtractor(@NotNull XValuePresentation.XValueTextRenderer renderer) {
    return renderer instanceof XValuePresentationTextExtractor;
  }

  @ApiStatus.Internal
  public static class XValuePresentationTextExtractor extends XValueTextRendererBase {
    private final StringBuilder myBuilder;

    public XValuePresentationTextExtractor() {
      myBuilder = new StringBuilder();
    }

    @Override
    public void renderValue(@NotNull String value) {
      myBuilder.append(value);
    }

    @Override
    protected void renderRawValue(@NotNull String value, @NotNull TextAttributesKey key) {
      myBuilder.append(value);
    }

    @Override
    public void renderStringValue(@NotNull String value, @Nullable String additionalSpecialCharsToHighlight, int maxLength) {
      myBuilder.append(value);
    }

    @Override
    public void renderComment(@NotNull String comment) {
      myBuilder.append(comment);
    }

    @Override
    public void renderError(@NotNull String error) {
      myBuilder.append(error);
    }

    @Override
    public void renderSpecialSymbol(@NotNull String symbol) {
      myBuilder.append(symbol);
    }

    public String getText() {
      return myBuilder.toString();
    }
  }
}
