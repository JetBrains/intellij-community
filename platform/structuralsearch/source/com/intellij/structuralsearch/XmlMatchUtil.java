// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.SmartList;
import com.intellij.xml.util.XmlUtil;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class XmlMatchUtil {

  private XmlMatchUtil() {}

  public static boolean isWhiteSpace(PsiElement element) {
    return element instanceof PsiWhiteSpace ||
           element instanceof XmlText &&
           element.getFirstChild() == element.getLastChild() &&
           element.getFirstChild() instanceof PsiWhiteSpace;
  }

  public static List<PsiElement> getElementsToMatch(XmlTagChild[] elements) {
    final List<PsiElement> list = new SmartList<>();
    for (XmlTagChild child : elements) {
      if (child instanceof XmlText) {
        for (PsiElement element : child.getChildren()) {
          if (XmlUtil.isXmlToken(element, XmlTokenType.XML_DATA_CHARACTERS)) list.add(element);
          else if (element instanceof XmlComment) list.add(element);
        }
      }
      else {
        list.add(child);
      }
    }
    return list;
  }
}
