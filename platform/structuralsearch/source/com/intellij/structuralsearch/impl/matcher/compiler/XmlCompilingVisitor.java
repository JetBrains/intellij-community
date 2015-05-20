package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.filters.TagValueFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler;
import com.intellij.structuralsearch.impl.matcher.strategies.XmlMatchingStrategy;

/**
* @author Eugene.Kudelevsky
*/
public class XmlCompilingVisitor extends XmlRecursiveElementVisitor {
  private final GlobalCompilingVisitor myCompilingVisitor;

  public XmlCompilingVisitor(GlobalCompilingVisitor compilingVisitor) {
    this.myCompilingVisitor = compilingVisitor;
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

    final MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(text);
    handler.setFilter(TagValueFilter.getInstance());
  }

  @Override public void visitXmlTag(XmlTag xmlTag) {
    myCompilingVisitor.setCodeBlockLevel(myCompilingVisitor.getCodeBlockLevel() + 1);
    super.visitXmlTag(xmlTag);
    myCompilingVisitor.setCodeBlockLevel(myCompilingVisitor.getCodeBlockLevel() - 1);

    if (myCompilingVisitor.getCodeBlockLevel() == 1) {
      final CompiledPattern pattern = myCompilingVisitor.getContext().getPattern();
      pattern.setStrategy(XmlMatchingStrategy.getInstance());
      pattern.setHandler(xmlTag, new TopLevelMatchingHandler(pattern.getHandler(xmlTag)));
    }
  }
}
