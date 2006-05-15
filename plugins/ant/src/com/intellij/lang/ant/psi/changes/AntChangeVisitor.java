package com.intellij.lang.ant.psi.changes;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.*;
import com.intellij.psi.xml.XmlElement;

public class AntChangeVisitor implements XmlChangeVisitor {

  public void visitXmlAttributeSet(final XmlAttributeSet xmlAttributeSet) {
    getAntParent(xmlAttributeSet.getTag()).clearCaches();
  }

  public void visitDocumentChanged(final XmlDocumentChanged xmlDocumentChanged) {
    getAntParent(xmlDocumentChanged.getDocument()).clearCaches();
  }

  public void visitXmlElementChanged(final XmlElementChanged xmlElementChanged) {
    getAntParent(xmlElementChanged.getElement()).clearCaches();
  }

  public void visitXmlTagChildAdd(final XmlTagChildAdd xmlTagChildAdd) {
    getAntParent(xmlTagChildAdd.getTag()).clearCaches();
  }

  public void visitXmlTagChildChanged(final XmlTagChildChanged xmlTagChildChanged) {
    getAntParent(xmlTagChildChanged.getTag()).clearCaches();
  }

  public void visitXmlTagChildRemoved(final XmlTagChildRemoved xmlTagChildRemoved) {
    getAntParent(xmlTagChildRemoved.getTag()).clearCaches();
  }

  public void visitXmlTagNameChanged(final XmlTagNameChanged xmlTagNameChanged) {
    getAntParent(xmlTagNameChanged.getTag()).clearCaches();
  }

  public void visitXmlTextChanged(final XmlTextChanged xmlTextChanged) {
    getAntParent(xmlTextChanged.getText()).clearCaches();
  }

  private static AntElement getAntParent(final XmlElement el) {
    final TextRange textRange = el.getTextRange();
    final AntFile antFile = (AntFile)el.getContainingFile().getViewProvider().getPsi(AntSupport.getLanguage());
    AntElement antElement = (AntElement)antFile.findElementAt(textRange.getStartOffset() + 1);
    while (!(antElement instanceof AntFile) && antElement.getTextLength() < textRange.getLength()) {
      antElement = antElement.getAntParent();
    }
    return antElement;
  }
}
