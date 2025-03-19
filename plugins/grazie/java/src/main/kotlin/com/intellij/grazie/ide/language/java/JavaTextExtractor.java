package com.intellij.grazie.ide.language.java;

import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextContent.Exclusion;
import com.intellij.grazie.text.TextContentBuilder;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.grazie.utils.HtmlUtilsKt;
import com.intellij.grazie.utils.PsiUtilsKt;
import com.intellij.grazie.utils.Text;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.source.javadoc.PsiDocTagImpl;
import com.intellij.psi.impl.source.tree.PsiCommentImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiInlineDocTag;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.intellij.grazie.text.TextContent.TextDomain.*;
import static com.intellij.psi.JavaDocTokenType.*;
import static com.intellij.psi.impl.source.tree.ElementType.JAVA_PLAIN_COMMENT_BIT_SET;
import static com.intellij.psi.impl.source.tree.JavaDocElementType.DOC_PARAMETER_REF;
import static com.intellij.psi.impl.source.tree.JavaDocElementType.DOC_REFERENCE_HOLDER;

public class JavaTextExtractor extends TextExtractor {
  private static final TokenSet EXCLUDED =
    TokenSet.create(DOC_COMMENT_START, DOC_COMMENT_LEADING_ASTERISKS, DOC_COMMENT_END, DOC_PARAMETER_REF, DOC_REFERENCE_HOLDER);
  private static final TextContentBuilder javadocBuilder = TextContentBuilder.FromPsi
    .withUnknown(e -> e instanceof PsiInlineDocTag)
    .excluding(e -> EXCLUDED.contains(PsiUtilCore.getElementType(e)))
    .removingIndents(" \t").removingLineSuffixes(" \t");

  @Override
  public @NotNull List<TextContent> buildTextContents(@NotNull PsiElement root, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    if (allowedDomains.contains(DOCUMENTATION)) {
      if (root instanceof PsiDocComment) {
        return HtmlUtilsKt.excludeHtml(javadocBuilder.excluding(e -> e instanceof PsiDocTagImpl).build(root, DOCUMENTATION));
      }
      if (root instanceof PsiDocTagImpl) {
        return HtmlUtilsKt.excludeHtml(javadocBuilder.build(root, DOCUMENTATION));
      }
    }

    if (root instanceof PsiCommentImpl && allowedDomains.contains(COMMENTS)) {
      List<PsiElement> roots = PsiUtilsKt.getNotSoDistantSimilarSiblings(root, e ->
        JAVA_PLAIN_COMMENT_BIT_SET.contains(PsiUtilCore.getElementType(e)));
      return ContainerUtil.createMaybeSingletonList(
        TextContent.joinWithWhitespace('\n', ContainerUtil.mapNotNull(roots, c ->
          TextContentBuilder.FromPsi.removingIndents(" \t*/").removingLineSuffixes(" \t").build(c, COMMENTS))));
    }

    if (root instanceof PsiLiteralExpression &&
        allowedDomains.contains(LITERALS) &&
        ((PsiLiteralExpression) root).getValue() instanceof String) {
      TextContent content = TextContentBuilder.FromPsi.build(root, LITERALS);
      int indent = PsiLiteralUtil.getTextBlockIndent((PsiLiteralExpression)root);
      if (indent >= 0 && indent < 1000 && content != null) {
        if (indent > 0) {
          content = content.excludeRanges(
            ContainerUtil.map(Text.allOccurrences(Pattern.compile("(?<=\n)" + "\\s{" + indent + "}"), content), Exclusion::exclude));
        }
        content = content.excludeRanges(ContainerUtil.map(Text.allOccurrences(Pattern.compile("\\\\\n"), content), Exclusion::exclude));
        return ContainerUtil.createMaybeSingletonList(content.trimWhitespace());
      }

      return ContainerUtil.createMaybeSingletonList(content);
    }

    return List.of();
  }

}
