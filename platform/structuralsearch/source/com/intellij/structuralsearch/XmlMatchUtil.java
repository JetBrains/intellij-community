// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.SmartList;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
@Internal
public final class XmlMatchUtil {

  private XmlMatchUtil() {}

  public static boolean isWhiteSpace(PsiElement element) {
    return element instanceof PsiWhiteSpace ||
           element instanceof XmlText &&
           element.getFirstChild() == element.getLastChild() &&
           element.getFirstChild() instanceof PsiWhiteSpace;
  }

  public static List<XmlElement> getElementsToMatch(XmlTagChild[] elements) {
    final List<XmlElement> list = new SmartList<>();
    for (XmlTagChild child : elements) {
      if (child instanceof XmlText) {
        for (PsiElement element : child.getChildren()) {
          if (element instanceof PsiWhiteSpace) continue;
          if (addSpecialXmlTags(element, list)) continue;
          if (XmlUtil.isXmlToken(element, XmlTokenType.XML_DATA_CHARACTERS)) list.add((XmlToken)element);
          else if (element instanceof XmlComment) list.add((XmlComment)element);
        }
      }
      else {
        list.add(child);
      }
    }
    return list;
  }

  public static PsiElement getElementToMatch(XmlAttributeValue attributeValue) {
    final PsiElement child = attributeValue.getFirstChild();
    if (!(child instanceof XmlToken token)) {
      return null;
    }
    if (token.getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
      return null;
    }
    final PsiElement sibling = child.getNextSibling();
    if (!(sibling instanceof XmlToken secondToken)) {
      return sibling;
    }
    return (secondToken.getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) ? null : secondToken;
  }

  private static boolean addSpecialXmlTags(@NotNull PsiElement element, List<? super XmlElement> list) {
    boolean result = false;
    for (SpecialElementExtractor extractor : SpecialElementExtractor.EP_NAME.getExtensionList()) {
      final PsiElement[] elements = extractor.extractSpecialElements(element);
      for (PsiElement specialElement : elements) {
        if (specialElement instanceof XmlTag) {
          list.add((XmlElement)specialElement);
          result = true;
        }
      }
    }
    return result;
  }
}
