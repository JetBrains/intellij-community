package com.intellij.grazie.text;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPlainText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class PlainTextExtractor extends TextExtractor {
  @Override
  public @Nullable TextContent buildTextContent(@NotNull PsiElement root,
                                                @NotNull Set<TextContent.TextDomain> allowedDomains) {
    if (root instanceof PsiPlainText && root.getContainingFile().getName().endsWith(".txt")) {
      return TextContent.builder().build(root, TextContent.TextDomain.PLAIN_TEXT);
    }
    return null;
  }
}
