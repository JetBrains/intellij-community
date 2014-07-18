package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.structuralsearch.impl.matcher.filters.TagValueFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.TopLevelMatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.XmlTextHandler;
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

  @Override public void visitXmlToken(XmlToken token) {
    super.visitXmlToken(token);

    if (token.getParent() instanceof XmlText && myCompilingVisitor.getContext().getPattern().isRealTypedVar(token)) {
      final MatchingHandler handler = myCompilingVisitor.getContext().getPattern().getHandler(token);
      handler.setFilter(TagValueFilter.getInstance());

      final XmlTextHandler parentHandler = new XmlTextHandler();
      myCompilingVisitor.getContext().getPattern().setHandler(token.getParent(), parentHandler);
      parentHandler.setFilter(TagValueFilter.getInstance());
    }
  }

  @Override public void visitXmlTag(XmlTag xmlTag) {
    myCompilingVisitor.setCodeBlockLevel(myCompilingVisitor.getCodeBlockLevel() + 1);
    super.visitXmlTag(xmlTag);
    myCompilingVisitor.setCodeBlockLevel(myCompilingVisitor.getCodeBlockLevel() - 1);

    if (myCompilingVisitor.getCodeBlockLevel() == 1) {
      myCompilingVisitor.getContext().getPattern().setStrategy(XmlMatchingStrategy.getInstance());
      myCompilingVisitor.getContext().getPattern()
        .setHandler(xmlTag, new TopLevelMatchingHandler(myCompilingVisitor.getContext().getPattern().getHandler(xmlTag)));
    }
  }
}
