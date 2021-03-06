// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.*;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.XmlMatchUtil;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.ListNodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
public class XmlMatchingVisitor extends XmlElementVisitor {
  private final GlobalMatchingVisitor myMatchingVisitor;

  public XmlMatchingVisitor(@NotNull GlobalMatchingVisitor matchingVisitor) {
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
    final XmlTag another = myMatchingVisitor.getElement(XmlTag.class);
    if (another == null) return;
    final CompiledPattern pattern = myMatchingVisitor.getMatchContext().getPattern();
    final XmlToken name = XmlUtil.getTokenOfType(tag, XmlTokenType.XML_NAME);
    final boolean isTypedVar = pattern.isTypedVar(name);

    if (!myMatchingVisitor.setResult((isTypedVar || myMatchingVisitor.matchText(tag.getName(), another.getName())) &&
                                     myMatchingVisitor.matchInAnyOrder(tag.getAttributes(), another.getAttributes()))) return;

    final XmlTagChild[] children1 = tag.getValue().getChildren();
    if (children1.length != 0) {
      if (children1.length == 1 && pattern.isTypedVar(children1[0])) {
        final NodeIterator patternNodes = SsrFilteringNodeIterator.create(tag.getValue().getChildren());
        if (patternNodes.current() != null) {
          final NodeIterator matchNodes = SsrFilteringNodeIterator.create(another.getValue().getChildren());
          if (!myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(patternNodes, matchNodes))) return;
        }
      }
      else if (children1.length != 1 || !XmlMatchUtil.isWhiteSpace(children1[0])) {
        final ListNodeIterator patternNodes = new ListNodeIterator(XmlMatchUtil.getElementsToMatch(children1));
        final XmlTagChild[] children2 = another.getValue().getChildren();
        final ListNodeIterator matchNodes = new ListNodeIterator(XmlMatchUtil.getElementsToMatch(children2));
        if (!myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(patternNodes, matchNodes))) return;
      }
    }
    if (isTypedVar) {
      final SubstitutionHandler handler = (SubstitutionHandler)pattern.getHandler(name);
      myMatchingVisitor.setResult(handler.handle(XmlUtil.getTokenOfType(another, XmlTokenType.XML_NAME),
                                                 myMatchingVisitor.getMatchContext()));
    }
  }

  @Override
  public void visitXmlText(XmlText text) {
    myMatchingVisitor.setResult(myMatchingVisitor.getMatchContext().getPattern().isTypedVar(text)
                                ? myMatchingVisitor.handleTypedElement(text, myMatchingVisitor.getElement())
                                : myMatchingVisitor.matchSequentially(text.getFirstChild(), myMatchingVisitor.getElement().getFirstChild()));
  }

  @Override public void visitXmlToken(XmlToken token) {
    if (token.getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
      final String text = token.getText();

      myMatchingVisitor.setResult(myMatchingVisitor.getMatchContext().getPattern().isTypedVar(text)
                                  ? myMatchingVisitor.handleTypedElement(token, myMatchingVisitor.getElement())
                                  : myMatchingVisitor.matchText(text, myMatchingVisitor.getElement().getText()));
    }
  }

  @Override
  public void visitXmlComment(XmlComment comment) {
    super.visitXmlComment(comment);
    final PsiElement element = myMatchingVisitor.getElement();
    if (!(element instanceof XmlComment)) return;
    final XmlComment other = (XmlComment)element;
    final XmlToken text = XmlUtil.getTokenOfType(comment, XmlTokenType.XML_COMMENT_CHARACTERS);
    assert text != null;
    final CompiledPattern pattern = myMatchingVisitor.getMatchContext().getPattern();
    final boolean typedVar = pattern.isTypedVar(text);
    if (typedVar) {
      final SubstitutionHandler handler = (SubstitutionHandler)pattern.getHandler(text);
      myMatchingVisitor.setResult(handler.handle(XmlUtil.getTokenOfType(other, XmlTokenType.XML_COMMENT_CHARACTERS),
                                                 myMatchingVisitor.getMatchContext()));
    }
    else {
      myMatchingVisitor.setResult(myMatchingVisitor.matchText(StructuralSearchUtil.normalize(text.getText()),
                                                              StructuralSearchUtil.normalize(other.getCommentText())));
    }
  }
}
