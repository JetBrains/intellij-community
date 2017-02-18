package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.*;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.dupLocator.iterators.ArrayBackedNodeIterator;

import java.util.ArrayList;
import java.util.List;

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

  @Override
  public void visitElement(final PsiElement element) {
    myMatchingVisitor.setResult(element.textMatches(element));
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
      MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(attribute.getName());
      myMatchingVisitor.setResult(((SubstitutionHandler)handler).handle(another, myMatchingVisitor.getMatchContext()));
    }
  }

  @Override public void visitXmlAttributeValue(XmlAttributeValue value) {
    final XmlAttributeValue another = (XmlAttributeValue)myMatchingVisitor.getElement();
    final String text = value.getValue();

    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(text);
    MatchingHandler handler;

    if (isTypedVar && (handler = myMatchingVisitor.getMatchContext().getPattern().getHandler( text )) instanceof SubstitutionHandler) {
      String text2 = another.getText();
      int offset = text2.length() > 0 && ( text2.charAt(0) == '"' || text2.charAt(0) == '\'') ? 1:0;
      myMatchingVisitor.setResult(((SubstitutionHandler)handler).handle(another, offset, text2.length() - offset,
                                                                        myMatchingVisitor.getMatchContext()));
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
      final XmlTagChild[] contentChildren = tag.getValue().getChildren();

      if (contentChildren.length > 0) {
        PsiElement[] patternNodes = contentChildren;
        PsiElement[] matchedNodes = another.getValue().getChildren();

        if (contentChildren.length != 1) {
          patternNodes = filterOutWhitespace(patternNodes);
          matchedNodes = filterOutWhitespace(matchedNodes);
        }

        final boolean result = myMatchingVisitor.matchSequentially(
          new ArrayBackedNodeIterator(patternNodes),
          new ArrayBackedNodeIterator(matchedNodes)
        );
        myMatchingVisitor.setResult(result);
      }
    }

    if (myMatchingVisitor.getResult() && isTypedVar) {
      final PsiElement[] children = another.getChildren();
      if (children.length > 1) {
        MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler( tag.getName() );
        myMatchingVisitor.setResult(((SubstitutionHandler)handler).handle(children[1], myMatchingVisitor.getMatchContext()));
      }
    }
  }

  private static PsiElement[] filterOutWhitespace(PsiElement[] children) {
    final List<PsiElement> result = new ArrayList<>(children.length);
    for(PsiElement child : children) {
      if (child instanceof XmlText) {
        final PsiElement[] grandChildren = child.getChildren();
        if (grandChildren.length == 1 && grandChildren[0] instanceof PsiWhiteSpace) {
          continue;
        }
      }
      result.add(child);
    }
    return PsiUtilCore.toPsiElementArray(result);
  }

  @Override public void visitXmlText(XmlText text) {
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(text);
    final PsiElement element = myMatchingVisitor.getElement();
    if (isTypedVar) {
      myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(text, element));
    } else {
      myMatchingVisitor.setResult(matches(text.getText(), element.getText()));
    }
  }

  private boolean matches(String a, String b) {
    return myCaseSensitive ? a.equals(b) : a.equalsIgnoreCase(b);
  }
}
