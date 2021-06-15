package com.intellij.grazie.ide.language.properties;

import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextContentBuilder;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.grazie.utils.HtmlUtilsKt;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PropertyTextExtractor extends TextExtractor {
  private static final Pattern FORMAT_ELEMENT = Pattern.compile("\\{.+?}");

  @Override
  public @Nullable TextContent buildTextContent(@NotNull PsiElement root,
                                                @NotNull Set<TextContent.TextDomain> allowedDomains) {
    if (root instanceof PsiComment) {
      return TextContentBuilder.FromPsi.removingIndents(" \t#").build(root, TextContent.TextDomain.COMMENTS);
    }
    if (PsiUtilCore.getElementType(root) == PropertiesTokenTypes.VALUE_CHARACTERS) {
      TextContent content = TextContent.psiFragment(TextContent.TextDomain.PLAIN_TEXT, root);
      while (true) {
        int apostrophes = content.toString().indexOf("''");
        if (apostrophes < 0) break;

        content = content.excludeRange(TextRange.from(apostrophes, 1));
      }

      while (true) {
        Matcher matcher = FORMAT_ELEMENT.matcher(content.toString());
        if (!matcher.find()) break;

        content = content.markUnknown(new TextRange(matcher.start(), matcher.end()));
      }
      return HtmlUtilsKt.removeHtml(content);
    }
    return null;
  }
}
