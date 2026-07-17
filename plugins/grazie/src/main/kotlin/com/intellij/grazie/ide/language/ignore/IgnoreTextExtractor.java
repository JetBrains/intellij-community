package com.intellij.grazie.ide.language.ignore;

import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextContent.TextDomain;
import com.intellij.grazie.text.TextContentBuilder;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.grazie.utils.PsiUtilsKt;
import com.intellij.openapi.vcs.changes.ignore.psi.IgnoreTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.intellij.grazie.text.TextContent.TextDomain.COMMENTS;

final class IgnoreTextExtractor extends TextExtractor {
  private static final TokenSet COMMENT_TOKENS =
    TokenSet.create(IgnoreTypes.COMMENT, IgnoreTypes.SECTION, IgnoreTypes.HEADER);

  @Override
  protected @NotNull List<TextContent> buildTextContents(@NotNull PsiElement root, @NotNull Set<TextDomain> allowedDomains) {
    if (!allowedDomains.contains(COMMENTS)) return List.of();
    if (!COMMENT_TOKENS.contains(PsiUtilCore.getElementType(root))) return List.of();

    List<PsiElement> roots = PsiUtilsKt.getNotSoDistantSimilarSiblings(root, e ->
      COMMENT_TOKENS.contains(PsiUtilCore.getElementType(e)));
    return ContainerUtil.createMaybeSingletonList(
      TextContent.joinWithWhitespace('\n', ContainerUtil.mapNotNull(roots, c ->
        TextContentBuilder.FromPsi.removingIndents(" \t#").build(c, COMMENTS))));
  }
}
