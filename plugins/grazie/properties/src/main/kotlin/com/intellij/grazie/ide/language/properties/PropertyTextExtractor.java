package com.intellij.grazie.ide.language.properties;

import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextContentBuilder;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.grazie.utils.HtmlUtilsKt;
import com.intellij.grazie.utils.PsiUtilsKt;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.intellij.grazie.text.TextContent.TextDomain.COMMENTS;

public class PropertyTextExtractor extends TextExtractor {
  @Override
  public @Nullable TextContent buildTextContent(@NotNull PsiElement root,
                                                @NotNull Set<TextContent.TextDomain> allowedDomains) {
    if (root instanceof PsiComment) {
      List<PsiElement> roots = PsiUtilsKt.getNotSoDistantSimilarSiblings(root, e ->
        PropertiesTokenTypes.COMMENTS.contains(PsiUtilCore.getElementType(e)));
      return TextContent.joinWithWhitespace(ContainerUtil.mapNotNull(roots, c ->
        TextContentBuilder.FromPsi.removingIndents(" \t#!").build(c, COMMENTS)));
    }
    if (PsiUtilCore.getElementType(root) == PropertiesTokenTypes.VALUE_CHARACTERS) {
      TextContent content = TextContent.builder().build(root, TextContent.TextDomain.PLAIN_TEXT);
      while (content != null) {
        int apostrophes = content.toString().indexOf("''");
        if (apostrophes < 0) break;

        content = content.excludeRange(TextRange.from(apostrophes, 1));
      }

      while (content != null) {
        String str = content.toString();
        int start = str.indexOf("{");
        if (start < 0) break;

        int nesting = 1;
        int end = start + 1;
        while (end < str.length()) {
          if (str.charAt(end) == '}' && --nesting == 0) {
            end++;
            break;
          }
          if (str.charAt(end) == '{') nesting++;
          end++;
        }
        content = content.markUnknown(new TextRange(start, end));
      }
      return HtmlUtilsKt.removeHtml(content);
    }
    return null;
  }
}
