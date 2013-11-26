package com.jetbrains.json.codeinsight;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.jetbrains.json.psi.JsonStringLiteral;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonStringLiteralAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof JsonStringLiteral) {
      String text = element.getText();
      int length = text.length();
      if (length <= 1 || text.charAt(length - 1) != '\"' || quoteEscaped(text, length - 1)) {
        holder.createErrorAnnotation(element.getTextRange(), "Missing closing quote");
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
