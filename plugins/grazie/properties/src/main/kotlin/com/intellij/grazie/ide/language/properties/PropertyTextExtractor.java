package com.intellij.grazie.ide.language.properties;

import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextContentBuilder;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class PropertyTextExtractor extends TextExtractor {
  @Override
  public @Nullable TextContent buildTextContent(@NotNull PsiElement root,
                                                @NotNull Set<TextContent.TextDomain> allowedDomains) {
    if (root instanceof PsiComment) {
      return TextContentBuilder.FromPsi.removingIndents(" \t#").build(root, TextContent.TextDomain.COMMENTS);
    }
    if (PsiUtilCore.getElementType(root) == PropertiesTokenTypes.VALUE_CHARACTERS) {
      return TextContent.psiFragment(TextContent.TextDomain.PLAIN_TEXT, root);
    }
    return null;
  }
}
