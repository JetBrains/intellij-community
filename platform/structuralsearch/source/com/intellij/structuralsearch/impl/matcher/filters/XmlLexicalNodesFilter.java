package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlText;

/**
* @author Eugene.Kudelevsky
*/
public class XmlLexicalNodesFilter extends XmlElementVisitor {
  private final LexicalNodesFilter myLexicalNodesFilter;

  public XmlLexicalNodesFilter(LexicalNodesFilter lexicalNodesFilter) {
    this.myLexicalNodesFilter = lexicalNodesFilter;
  }

  @Override public void visitXmlText(XmlText text) {
    myLexicalNodesFilter.setResult(true);
  }
}
