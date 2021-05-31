package com.intellij.grazie.ide.language.xml;

import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextContentBuilder;
import com.intellij.grazie.text.TextExtractor;
import com.intellij.lang.Language;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class XmlTextExtractor extends TextExtractor {
  private static final TextContentBuilder builder = TextContentBuilder.FromPsi.removingIndents(" \t");
  private final Set<Class<? extends Language>> myEnabledDialects;

  protected XmlTextExtractor(Class<? extends Language>... enabledDialects) {
    myEnabledDialects = Set.of(enabledDialects);
  }

  @Override
  protected @Nullable TextContent buildTextContent(@NotNull PsiElement element,
                                                   @NotNull Set<TextContent.TextDomain> allowedDomains) {
    if (!myEnabledDialects.contains(element.getLanguage().getClass()) ||
        PsiTreeUtil.getParentOfType(element, XmlProlog.class) != null) {
      return null;
    }

    if (element instanceof XmlText && !isNonText(((XmlText) element).getParentTag())) {
      return markEdgesUnknown(builder
        .excluding(e -> PsiUtilCore.getElementType(e) == XmlElementType.XML_CDATA)
        .build(element, TextContent.TextDomain.PLAIN_TEXT));
    }

    IElementType type = PsiUtilCore.getElementType(element);
    if (type == XmlTokenType.XML_DATA_CHARACTERS &&
        PsiUtilCore.getElementType(element.getParent()) == XmlElementType.XML_CDATA) {
      return markEdgesUnknown(builder.build(element, TextContent.TextDomain.PLAIN_TEXT));
    }

    if (type == XmlTokenType.XML_COMMENT_CHARACTERS) {
      return builder.build(element, TextContent.TextDomain.COMMENTS);
    }

    if (type == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
      return builder.build(element, TextContent.TextDomain.LITERALS);
    }

    return null;
  }

  private static boolean isNonText(XmlTag tag) {
    return tag instanceof HtmlTag && "code".equals(tag.getName());
  }

  // We treat tag contents separately, even if they're inline and in fact concatenated.
  // So for now we ignore problems that rely on the edges of the text.
  private static TextContent markEdgesUnknown(@Nullable TextContent content) {
    return content == null ? null :
           content.markUnknown(TextRange.from(0, 0)).markUnknown(TextRange.from(content.length(), 0));
  }

  public static class Xml extends XmlTextExtractor {
    public Xml() {
      super(XMLLanguage.class, XHTMLLanguage.class, DTDLanguage.class);
    }
  }

  public static class Html extends XmlTextExtractor {
    public Html() {
      super(HTMLLanguage.class);
    }
  }
}
