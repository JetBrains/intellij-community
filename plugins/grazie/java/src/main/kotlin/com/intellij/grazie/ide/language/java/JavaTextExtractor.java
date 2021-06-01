package com.intellij.grazie.ide.language.java;

import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextContentBuilder;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.grazie.utils.PsiUtilsKt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.source.javadoc.PsiDocTagImpl;
import com.intellij.psi.impl.source.tree.PsiCommentImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.intellij.grazie.text.TextContent.TextDomain.*;
import static com.intellij.psi.JavaDocTokenType.*;
import static com.intellij.psi.impl.source.tree.ElementType.JAVA_PLAIN_COMMENT_BIT_SET;
import static com.intellij.psi.impl.source.tree.JavaDocElementType.DOC_PARAMETER_REF;

public class JavaTextExtractor extends TextExtractor {
  private static final TokenSet EXCLUDED =
    TokenSet.create(DOC_COMMENT_START, DOC_COMMENT_LEADING_ASTERISKS, DOC_COMMENT_END, DOC_PARAMETER_REF);
  private static final TextContentBuilder javadocBuilder = TextContentBuilder.FromPsi
    .withUnknown(e -> e instanceof PsiInlineDocTag)
    .excluding(e -> EXCLUDED.contains(PsiUtilCore.getElementType(e)))
    .removingIndents(" \t");

  @Override
  public TextContent buildTextContent(@NotNull PsiElement root, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    if (allowedDomains.contains(DOCUMENTATION)) {
      if (root instanceof PsiDocComment) {
        return javadocBuilder.excluding(e -> e instanceof PsiDocTagImpl).build(root, DOCUMENTATION);
      }
      if (root instanceof PsiDocTagImpl) {
        return javadocBuilder.build(root, DOCUMENTATION);
      }
    }

    if (root instanceof PsiCommentImpl && allowedDomains.contains(COMMENTS)) {
      List<PsiElement> roots = PsiUtilsKt.getNotSoDistantSimilarSiblings(root, TokenSet.WHITE_SPACE,
        e -> JAVA_PLAIN_COMMENT_BIT_SET.contains(PsiUtilCore.getElementType(e)));
      return TextContent.joinWithWhitespace(ContainerUtil.mapNotNull(roots, c ->
        TextContentBuilder.FromPsi.removingIndents(" \t*/").build(c, COMMENTS)));
    }

    if (root instanceof PsiLiteralExpression &&
        allowedDomains.contains(LITERALS) &&
        ((PsiLiteralExpression) root).getValue() instanceof String) {
      return TextContentBuilder.FromPsi.build(root, LITERALS);
    }

    return null;
  }

}
