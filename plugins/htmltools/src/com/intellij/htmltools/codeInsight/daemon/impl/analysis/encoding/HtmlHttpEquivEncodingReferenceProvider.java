package com.intellij.htmltools.codeInsight.daemon.impl.analysis.encoding;

import com.intellij.codeInsight.daemon.impl.analysis.encoding.XmlEncodingReferenceProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class HtmlHttpEquivEncodingReferenceProvider extends XmlEncodingReferenceProvider {
  private static final Logger LOG = Logger.getInstance(XmlEncodingReferenceProvider.class);

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, final @NotNull ProcessingContext context) {
    LOG.assertTrue(element instanceof XmlAttributeValue);
    XmlAttributeValue value = (XmlAttributeValue)element;
    PsiElement parent = value.getParent();
    if (parent instanceof XmlAttribute attribute) {
      XmlTag tag = attribute.getParent();
      @NonNls String name = attribute.getLocalName();
      if (tag != null && "meta".equals(tag.getLocalName()) && tag.getAttribute("http-equiv") != null && name.equals("content")) {
        return extractFromContentAttribute(value);
      }
    }

    return PsiReference.EMPTY_ARRAY;
  }
}
