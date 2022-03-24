package com.intellij.grazie.text;

import com.intellij.grazie.utils.Text;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainText;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class PlainTextExtractor extends TextExtractor {
  private static final Pattern paragraphEnd = Pattern.compile("\\n\\s*?\\n\\s*");

  @Override
  protected @NotNull List<TextContent> buildTextContents(@NotNull PsiElement root, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    if (root instanceof PsiPlainText && root.getContainingFile().getName().endsWith(".txt")) {
      String text = root.getText();
      List<TextContent> result = new ArrayList<>();
      int[] ends = StreamEx.of(Text.allOccurrences(paragraphEnd, text)).mapToInt(TextRange::getStartOffset).append(text.length()).toArray();
      for (int i = 0; i < ends.length; i++) {
        int start = i == 0 ? 0 : ends[i - 1];
        int end = ends[i];
        ContainerUtil.addIfNotNull(result, TextContent.builder().build(root, TextContent.TextDomain.PLAIN_TEXT, new TextRange(start, end)));
      }
      return result;
    }
    return Collections.emptyList();
  }
}
