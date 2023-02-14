package com.intellij.htmltools.xml.util;

import com.intellij.htmltools.codeInsight.daemon.impl.analysis.encoding.HtmlHttpEquivEncodingReferenceProvider;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.ParentElementFilter;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

final class HtmlReferenceContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    HtmlReferenceProvider provider = new HtmlReferenceProvider();
    String[] htmlAttrs = HtmlReferenceProvider.getAttributeValues();
    ElementFilter htmlFilter = HtmlReferenceProvider.getFilter();
    XmlUtil.registerXmlAttributeValueReferenceProvider(registrar, htmlAttrs, htmlFilter, false, provider);

    XmlUtil.registerXmlAttributeValueReferenceProvider(registrar, new String[] {"content"}, new ScopeFilter(
      new ParentElementFilter(
        new AndFilter(XmlTagFilter.INSTANCE, new XmlTextFilter("meta")), 2
      )
    ), true, new HtmlHttpEquivEncodingReferenceProvider());

  }
}
