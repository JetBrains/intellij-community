// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.filters.TagValueFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler;

import static com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor.OccurenceKind.CODE;
import static com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor.OccurenceKind.TEXT;

/**
* @author Eugene.Kudelevsky
*/
public class XmlCompilingVisitor extends XmlRecursiveElementVisitor {
  final GlobalCompilingVisitor myCompilingVisitor;

  public XmlCompilingVisitor(GlobalCompilingVisitor compilingVisitor) {
    this.myCompilingVisitor = compilingVisitor;
  }

  public void compile(PsiElement[] topLevelElements) {
    final XmlWordOptimizer optimizer = new XmlWordOptimizer();
    final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
    for (PsiElement element : topLevelElements) {
      element.accept(this);
      element.accept(optimizer);
      pattern.setHandler(element, new TopLevelMatchingHandler(pattern.getHandler(element)));
    }
  }

  private class XmlWordOptimizer extends XmlRecursiveElementWalkingVisitor implements WordOptimizer {

    @Override
    public void visitXmlTag(XmlTag tag) {
      if (!handleWord(tag.getName(), CODE, myCompilingVisitor.getContext())) return;
      super.visitXmlTag(tag);
    }

    @Override
    public void visitXmlAttribute(XmlAttribute attribute) {
      if (!handleWord(attribute.getName(), CODE, myCompilingVisitor.getContext())) return;
      handleWord(attribute.getValue(), CODE, myCompilingVisitor.getContext());
      super.visitXmlAttribute(attribute);
    }

    @Override
    public void visitXmlToken(XmlToken token) {
      super.visitXmlToken(token);
      final IElementType tokenType = token.getTokenType();
      if (tokenType == XmlTokenType.XML_COMMENT_CHARACTERS ||
          tokenType == XmlTokenType.XML_DATA_CHARACTERS) {
        handleWord(token.getText(), TEXT, myCompilingVisitor.getContext());
      }
    }
  }

  @Override
  public void visitElement(PsiElement element) {
    myCompilingVisitor.handle(element);
    super.visitElement(element);
  }

  @Override
  public void visitXmlToken(XmlToken token) {
    final IElementType tokenType = token.getTokenType();
    if (tokenType != XmlTokenType.XML_NAME &&
        tokenType != XmlTokenType.XML_COMMENT_CHARACTERS &&
        tokenType != XmlTokenType.XML_DATA_CHARACTERS) {
      return;
    }
    super.visitXmlToken(token);
    if (tokenType == XmlTokenType.XML_DATA_CHARACTERS) {
      myCompilingVisitor.setFilterSimple(token, TagValueFilter.getInstance());
    }
  }

  @Override
  public void visitXmlText(XmlText text) {
    super.visitXmlText(text);
    if (myCompilingVisitor.getContext().getPattern().isRealTypedVar(text)) {
      myCompilingVisitor.setFilterSimple(text, TagValueFilter.getInstance());
    }
  }
}
