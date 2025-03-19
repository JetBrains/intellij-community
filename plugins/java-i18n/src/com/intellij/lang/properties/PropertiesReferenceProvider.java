// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.jsp.jspXml.JspXmlTagBase;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastContextKt;

import java.util.Arrays;

public class PropertiesReferenceProvider extends PsiReferenceProvider {
  private final UastPropertiesReferenceProvider myProvider;
  private final boolean myDefaultSoft;

  // used by reflection
  @SuppressWarnings("unused")
  public PropertiesReferenceProvider() {
    this(false);
  }

  public PropertiesReferenceProvider(boolean defaultSoft) {
    myProvider = new UastPropertiesReferenceProvider(defaultSoft);
    myDefaultSoft = defaultSoft;
  }

  @Override
  public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    if (element instanceof XmlAttributeValue && isNonDynamicAttribute(element)) {
      if (element.getTextLength() < 2) {
        return PsiReference.EMPTY_ARRAY;
      }
      String value = ((XmlAttributeValue)element).getValue();
      final XmlAttribute attribute = (XmlAttribute)element.getParent();

      boolean propertyRefWithPrefix = false;
      if ("key".equals(attribute.getName())) {
        final XmlTag parent = attribute.getParent();
        if ("message".equals(parent.getLocalName()) && Arrays.binarySearch(XmlUtil.JSTL_FORMAT_URIS, parent.getNamespace()) >= 0) {
          propertyRefWithPrefix = true;
        }
      }

      PsiReference reference = propertyRefWithPrefix ?
                               new PrefixBasedPropertyReference(value, element, null, myDefaultSoft) :
                               new PropertyReference(value, element, null, myDefaultSoft);
      return new PsiReference[]{reference};
    }
    UElement uElement = UastContextKt.toUElement(element, ULiteralExpression.class);
    return uElement == null ? PsiReference.EMPTY_ARRAY : myProvider.getReferencesByElement(uElement, context);
  }

  static boolean isNonDynamicAttribute(PsiElement element) {
    return PsiTreeUtil.getChildOfAnyType(element, OuterLanguageElement.class, JspXmlTagBase.class) == null;
  }

  @Override
  public boolean acceptsHints(@NotNull PsiElement element, PsiReferenceService.@NotNull Hints hints) {
    if (!myProvider.acceptsHint(hints)) return false;

    return super.acceptsHints(element, hints);
  }

  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return myProvider.acceptsTarget(target);
  }
}
