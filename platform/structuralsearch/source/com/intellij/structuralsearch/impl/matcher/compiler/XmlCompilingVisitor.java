// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.filters.TagValueFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler;

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
      if (!handleWord(tag.getName(), myCompilingVisitor.getContext())) return;
      super.visitXmlTag(tag);
    }

    @Override
    public void visitXmlAttribute(XmlAttribute attribute) {
      if (!handleWord(attribute.getName(), myCompilingVisitor.getContext())) return;
      handleWord(attribute.getValue(), myCompilingVisitor.getContext());
      super.visitXmlAttribute(attribute);
    }

    @Override
    public void visitXmlText(XmlText text) {
      final String string = text.getText();
      if (!myCompilingVisitor.getContext().getPattern().isTypedVar(string)) {
        myCompilingVisitor.processTokenizedName(string, false, TEXT);
      }
      super.visitXmlText(text);
    }
  }

  @Override public void visitElement(PsiElement element) {
    myCompilingVisitor.handle(element);
    super.visitElement(element);
  }

  @Override
  public void visitXmlToken(XmlToken token) {}

  @Override
  public void visitXmlText(XmlText text) {
    super.visitXmlText(text);
    myCompilingVisitor.setFilterSimple(text, TagValueFilter.getInstance());
  }
}
