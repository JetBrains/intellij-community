package com.intellij.grazie.ide.language.properties;

import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextContent.Exclusion;
import com.intellij.grazie.text.TextContentBuilder;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.grazie.utils.HtmlUtilsKt;
import com.intellij.grazie.utils.PsiUtilsKt;
import com.intellij.grazie.utils.Text;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.spellchecker.MnemonicsTokenizer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.intellij.grazie.text.TextContent.TextDomain.COMMENTS;

final class PropertyTextExtractor extends TextExtractor {

  private static final ExtensionPointName<MnemonicsTokenizer> MNEMONICS_EP_NAME =
    ExtensionPointName.create("com.intellij.properties.spellcheckerMnemonicsTokenizer");

  private static final Pattern apostrophes = Pattern.compile("'(?=')");
  private static final Pattern continuationIndent = Pattern.compile("(?<=\n)[ \t]+");
  private static final Pattern trailingSlash = Pattern.compile("\\\\\n");

  @Override
  protected @NotNull List<TextContent> buildTextContents(@NotNull PsiElement root, @NotNull Set<TextContent.TextDomain> allowedDomains) {
    if (root instanceof PsiComment) {
      List<PsiElement> roots = PsiUtilsKt.getNotSoDistantSimilarSiblings(root, e ->
        PropertiesTokenTypes.COMMENTS.contains(PsiUtilCore.getElementType(e)));
      return ContainerUtil.createMaybeSingletonList(
        TextContent.joinWithWhitespace('\n', ContainerUtil.mapNotNull(roots, c ->
          TextContentBuilder.FromPsi.removingIndents(" \t#!").build(c, COMMENTS))));
    }
    if (PsiUtilCore.getElementType(root) == PropertiesTokenTypes.VALUE_CHARACTERS) {
      TextContent content = TextContent.builder().build(root, TextContent.TextDomain.PLAIN_TEXT);
      if (content != null) {
        content = content.excludeRanges(ContainerUtil.map(Text.allOccurrences(apostrophes, content), Exclusion::exclude));
        content = content.excludeRanges(ContainerUtil.map(Text.allOccurrences(continuationIndent, content), Exclusion::exclude));
        content = content.excludeRanges(ContainerUtil.map(Text.allOccurrences(trailingSlash, content), Exclusion::exclude));
        for (MnemonicsTokenizer tokenizer : MNEMONICS_EP_NAME.getExtensionList()) {
          if (tokenizer.hasMnemonics(content.toString())) {
            Pattern ignoredCharactersPattern = Pattern.compile(
              "[" +
              tokenizer.ignoredCharacters().stream()
                .map(String::valueOf)
                .collect(Collectors.joining()) +
              "]"
            );
            content = content.excludeRanges(ContainerUtil.map(Text.allOccurrences(ignoredCharactersPattern, content), Exclusion::exclude));
            break;
          }
        }
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
      return HtmlUtilsKt.excludeHtml(content);
    }
    return List.of();
  }
}
