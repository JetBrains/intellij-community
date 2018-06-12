// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.dupLocator.iterators.ArrayBackedNodeIterator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.*;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;
import com.intellij.xml.util.XmlUtil;

/**
* @author Eugene.Kudelevsky
*/
public class XmlMatchingVisitor extends XmlElementVisitor {
  private final GlobalMatchingVisitor myMatchingVisitor;

  public XmlMatchingVisitor(GlobalMatchingVisitor matchingVisitor) {
    myMatchingVisitor = matchingVisitor;
  }

  @Override public void visitXmlAttribute(XmlAttribute attribute) {
    final XmlAttribute another = (XmlAttribute)myMatchingVisitor.getElement();
    myMatchingVisitor.getMatchContext().pushResult();
    final XmlElement name = attribute.getNameElement();
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(name);
    try {
      if (!myMatchingVisitor.setResult(isTypedVar || myMatchingVisitor.matchText(name, another.getNameElement()))) return;
      final XmlAttributeValue valueElement = attribute.getValueElement();
      myMatchingVisitor.setResult(valueElement == null || myMatchingVisitor.matchOptionally(valueElement, another.getValueElement()));
    } finally {
      myMatchingVisitor.scopeMatch(name, isTypedVar, another);
    }
  }

  @Override public void visitXmlAttributeValue(XmlAttributeValue value) {
    final XmlAttributeValue another = (XmlAttributeValue)myMatchingVisitor.getElement();
    final String text = value.getValue();

    if (myMatchingVisitor.getMatchContext().getPattern().isTypedVar(text)) {
      final SubstitutionHandler handler = (SubstitutionHandler)myMatchingVisitor.getMatchContext().getPattern().getHandler(text);
      final String text2 = another.getText();
      final int offset = StringUtil.isQuotedString(text2) ? 1 : 0;
      myMatchingVisitor.setResult(handler.handle(another, offset, text2.length() - offset, myMatchingVisitor.getMatchContext()));
    } else {
      myMatchingVisitor.setResult(myMatchingVisitor.matchText(text, another.getValue()));
    }
  }

  @Override public void visitXmlTag(XmlTag tag) {
    final XmlTag another = (XmlTag)myMatchingVisitor.getElement();
    final CompiledPattern pattern = myMatchingVisitor.getMatchContext().getPattern();
    final XmlToken name = XmlUtil.getTokenOfType(tag, XmlTokenType.XML_NAME);
    final boolean isTypedVar = pattern.isTypedVar(name);

    if (!myMatchingVisitor.setResult((isTypedVar || myMatchingVisitor.matchText(tag.getName(), another.getName())) &&
                                     myMatchingVisitor.matchInAnyOrder(tag.getAttributes(), another.getAttributes()))) return;

    final SsrFilteringNodeIterator patternNodes = new SsrFilteringNodeIterator(new ArrayBackedNodeIterator(tag.getValue().getChildren()));
    if (patternNodes.current() != null) {
      final SsrFilteringNodeIterator matchNodes =
        new SsrFilteringNodeIterator(new ArrayBackedNodeIterator(another.getValue().getChildren()));
      if (!myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(patternNodes, matchNodes))) return;
    }

    if (isTypedVar) {
      final SubstitutionHandler handler = (SubstitutionHandler)pattern.getHandler(name);
      myMatchingVisitor.setResult(handler.handle(XmlUtil.getTokenOfType(another, XmlTokenType.XML_NAME),
                                                 myMatchingVisitor.getMatchContext()));
    }
  }

  @Override public void visitXmlText(XmlText text) {
    final PsiElement element = myMatchingVisitor.getElement();
    myMatchingVisitor.setResult(myMatchingVisitor.getMatchContext().getPattern().isTypedVar(text)
                                ? myMatchingVisitor.handleTypedElement(text, element)
                                : element instanceof XmlText && myMatchingVisitor.matchText(text.getText().trim(), element.getText().trim()));
  }
}
