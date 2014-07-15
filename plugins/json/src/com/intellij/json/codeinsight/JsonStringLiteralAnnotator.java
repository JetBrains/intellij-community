package com.intellij.json.codeinsight;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.json.psi.JsonStringLiteral;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonStringLiteralAnnotator implements Annotator {

  @NonNls public static final String MISSING_CLOSING_QUOTE = "Missing closing quote";
  @NonNls public static final String ILLEGAL_ESCAPE_SEQUENCE = "Illegal escape sequence";
  @NonNls public static final String ILLEGAL_UNICODE_ESCAPE_SEQUENCE = "Illegal unicode escape sequence";

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof JsonStringLiteral) {
      String text = element.getText();
      int offset = element.getTextOffset();
      int length = text.length();
      // Check that string literal closed properly
      if (length <= 1 || text.charAt(length - 1) != '\"' || quoteEscaped(text, length - 1)) {
        holder.createErrorAnnotation(element.getTextRange(), MISSING_CLOSING_QUOTE);
      }
      // Check escape sequences validity
      int pos = 1;
      while (pos < length) {
        if (text.charAt(pos) == '\\') {
          if (pos >= length - 1) {
            TextRange range = new TextRange(offset + pos, offset + pos + 1);
            holder.createErrorAnnotation(range, ILLEGAL_ESCAPE_SEQUENCE);
            break;
          }
          char next = text.charAt(pos + 1);
          switch (next) {
            case '"':
            case '\\':
            case '/':
            case 'b':
            case 'f':
            case 'n':
            case 'r':
            case 't':
              pos += 2;
              break;
            case 'u':
              int i = pos + 2;
              for (; i < pos + 6; i++) {
                if (i == length || !StringUtil.isHexDigit(text.charAt(i))) {
                  TextRange range = new TextRange(offset + pos, offset + i);
                  holder.createErrorAnnotation(range, ILLEGAL_UNICODE_ESCAPE_SEQUENCE);
                  break;
                }
              }
              pos = i;
              break;
            default:
              TextRange range = new TextRange(offset + pos, offset + pos + 2);
              holder.createErrorAnnotation(range, ILLEGAL_ESCAPE_SEQUENCE);
              pos += 2;
          }
        }
        else {
          pos++;
        }
      }
    }
  }

  private static boolean quoteEscaped(String text, int quotePos) {
    int count = 0;
    for (int i = quotePos - 1; i >= 0 && text.charAt(i) == '\\'; i--) {
      count++;
    }
    return count % 2 != 0;
  }
}
