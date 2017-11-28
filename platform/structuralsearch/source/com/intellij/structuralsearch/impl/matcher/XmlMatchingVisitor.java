/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.dupLocator.iterators.ArrayBackedNodeIterator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.*;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;

/**
* @author Eugene.Kudelevsky
*/
public class XmlMatchingVisitor extends XmlElementVisitor {
  private final GlobalMatchingVisitor myMatchingVisitor;
  private final boolean myCaseSensitive;

  public XmlMatchingVisitor(GlobalMatchingVisitor matchingVisitor) {
    myMatchingVisitor = matchingVisitor;
    myCaseSensitive = myMatchingVisitor.getMatchContext().getOptions().isCaseSensitiveMatch();
  }

  @Override public void visitXmlAttribute(XmlAttribute attribute) {
    final XmlAttribute another = (XmlAttribute)myMatchingVisitor.getElement();
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(attribute.getName());

    myMatchingVisitor.setResult(isTypedVar || matches(attribute.getName(), another.getName()));
    final XmlAttributeValue valueElement = attribute.getValueElement();
    if (myMatchingVisitor.getResult() && valueElement != null) {
      myMatchingVisitor.setResult(myMatchingVisitor.match(valueElement, another.getValueElement()));
    }

    if (myMatchingVisitor.getResult() && isTypedVar) {
      final SubstitutionHandler handler =
        (SubstitutionHandler)myMatchingVisitor.getMatchContext().getPattern().getHandler(attribute.getName());
      myMatchingVisitor.setResult(handler.handle(another, myMatchingVisitor.getMatchContext()));
    }
  }

  @Override public void visitXmlAttributeValue(XmlAttributeValue value) {
    final XmlAttributeValue another = (XmlAttributeValue)myMatchingVisitor.getElement();
    final String text = value.getValue();

    if (myMatchingVisitor.getMatchContext().getPattern().isTypedVar(text)) {
      final SubstitutionHandler handler = (SubstitutionHandler)myMatchingVisitor.getMatchContext().getPattern().getHandler(text);
      final String text2 = another.getText();
      final int offset = text2.length() > 0 && (text2.charAt(0) == '"' || text2.charAt(0) == '\'') ? 1 : 0;
      myMatchingVisitor.setResult(handler.handle(another, offset, text2.length() - offset, myMatchingVisitor.getMatchContext()));
    } else {
      myMatchingVisitor.setResult(matches(text, another.getValue()));
    }
  }

  @Override public void visitXmlTag(XmlTag tag) {
    final XmlTag another = (XmlTag)myMatchingVisitor.getElement();
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(tag.getName());

    myMatchingVisitor.setResult((matches(tag.getName(), another.getName()) || isTypedVar) &&
                                myMatchingVisitor.matchInAnyOrder(tag.getAttributes(), another.getAttributes()));

    if(myMatchingVisitor.getResult()) {
      final XmlTagChild[] children = tag.getValue().getChildren();

      final SsrFilteringNodeIterator patternNodes = new SsrFilteringNodeIterator(new ArrayBackedNodeIterator(children));
      if (patternNodes.current() != null) {
        final SsrFilteringNodeIterator matchNodes =
          new SsrFilteringNodeIterator(new ArrayBackedNodeIterator(another.getValue().getChildren()));
        myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(patternNodes, matchNodes));
      }
    }

    if (myMatchingVisitor.getResult() && isTypedVar) {
      final PsiElement[] children = another.getChildren();
      if (children.length > 1) {
        final SubstitutionHandler handler = (SubstitutionHandler)myMatchingVisitor.getMatchContext().getPattern().getHandler(tag.getName());
        myMatchingVisitor.setResult(handler.handle(children[1], myMatchingVisitor.getMatchContext()));
      }
    }
  }

  @Override public void visitXmlText(XmlText text) {
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(text);
    final PsiElement element = myMatchingVisitor.getElement();
    if (isTypedVar) {
      myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(text, element));
    }
    else {
      myMatchingVisitor.setResult(element instanceof XmlText && matches(text.getText().trim(), element.getText().trim()));
    }
  }

  private boolean matches(String a, String b) {
    return myCaseSensitive ? a.equals(b) : a.equalsIgnoreCase(b);
  }
}
