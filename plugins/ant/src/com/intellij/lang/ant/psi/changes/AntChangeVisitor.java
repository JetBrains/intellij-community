package com.intellij.lang.ant.psi.changes;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.*;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.Nullable;

public class AntChangeVisitor implements XmlChangeVisitor {

  public void visitXmlAttributeSet(final XmlAttributeSet xmlAttributeSet) {
    final AntElement element = getAntParent(xmlAttributeSet.getTag());
    if (element != null) {
      element.clearCaches();
    }
  }

  public void visitDocumentChanged(final XmlDocumentChanged xmlDocumentChanged) {
    final AntElement element = getAntParent(xmlDocumentChanged.getDocument());
    if (element != null) {
      element.clearCaches();
    }
  }

  public void visitXmlElementChanged(final XmlElementChanged xmlElementChanged) {
    final AntElement element = getAntParent(xmlElementChanged.getElement());
    if (element != null) {
      element.clearCaches();
    }
  }

  public void visitXmlTagChildAdd(final XmlTagChildAdd xmlTagChildAdd) {
    final AntElement element = getAntParent(xmlTagChildAdd.getTag());
    if (element != null) {
      element.clearCaches();
    }
  }

  public void visitXmlTagChildChanged(final XmlTagChildChanged xmlTagChildChanged) {
    final AntElement element = getAntParent(xmlTagChildChanged.getTag());
    if (element != null) {
      element.clearCaches();
    }
  }

  public void visitXmlTagChildRemoved(final XmlTagChildRemoved xmlTagChildRemoved) {
    final AntElement element = getAntParent(xmlTagChildRemoved.getTag());
    if (element != null) {
      element.clearCaches();
    }
  }

  public void visitXmlTagNameChanged(final XmlTagNameChanged xmlTagNameChanged) {
    final AntElement element = getAntParent(xmlTagNameChanged.getTag());
    if (element != null) {
      element.clearCaches();
    }
  }

  public void visitXmlTextChanged(final XmlTextChanged xmlTextChanged) {
    final AntElement element = getAntParent(xmlTextChanged.getText());
    if (element != null) {
      element.clearCaches();
    }
  }

  @Nullable
  private static AntElement getAntParent(final XmlElement el) {
    final TextRange textRange = el.getTextRange();
    final AntFile antFile = (AntFile)el.getContainingFile().getViewProvider().getPsi(AntSupport.getLanguage());
    if (antFile == null) return null;
    AntElement antElement = (AntElement)antFile.findElementAt(textRange.getStartOffset());
    while (!(antElement instanceof AntFile) && antElement.getTextLength() < textRange.getLength()) {
      antElement = antElement.getAntParent();
    }
    return antElement;
  }
}
