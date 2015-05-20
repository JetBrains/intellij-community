package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.XmlElementVisitor;

/**
* @author Eugene.Kudelevsky
*/
public class XmlLexicalNodesFilter extends XmlElementVisitor {
  private final LexicalNodesFilter myLexicalNodesFilter;

  public XmlLexicalNodesFilter(LexicalNodesFilter lexicalNodesFilter) {
    this.myLexicalNodesFilter = lexicalNodesFilter;
  }

  @Override
  public void visitWhiteSpace(PsiWhiteSpace space) {
    myLexicalNodesFilter.setResult(true);
  }

  @Override
  public void visitErrorElement(PsiErrorElement element) {
    myLexicalNodesFilter.setResult(true);
  }
}
