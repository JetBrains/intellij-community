package com.intellij.lang.ant.psi.changes;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntMacroDef;
import com.intellij.lang.ant.psi.impl.AntOuterProjectElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.Nullable;

public class AntChangeVisitor implements XmlChangeVisitor {

  public void visitXmlAttributeSet(final XmlAttributeSet xmlAttributeSet) {
    clearParentCaches(xmlAttributeSet.getTag());
  }

  public void visitDocumentChanged(final XmlDocumentChanged xmlDocumentChanged) {
    final AntFile antFile = (AntFile)xmlDocumentChanged.getDocument().getContainingFile().getViewProvider()
      .getPsi(AntSupport.getLanguage());
    if (antFile != null) {
      antFile.clearCaches();
    }
  }

  public void visitXmlElementChanged(final XmlElementChanged xmlElementChanged) {
    clearParentCaches(xmlElementChanged.getElement());
  }

  public void visitXmlTagChildAdd(final XmlTagChildAdd xmlTagChildAdd) {
    clearParentCaches(xmlTagChildAdd.getTag());
  }

  public void visitXmlTagChildChanged(final XmlTagChildChanged xmlTagChildChanged) {
    clearParentCaches(xmlTagChildChanged.getTag());
  }

  public void visitXmlTagChildRemoved(final XmlTagChildRemoved xmlTagChildRemoved) {
    clearParentCaches(xmlTagChildRemoved.getTag());
  }

  public void visitXmlTagNameChanged(final XmlTagNameChanged xmlTagNameChanged) {
    clearParentCaches(xmlTagNameChanged.getTag());
  }

  public void visitXmlTextChanged(final XmlTextChanged xmlTextChanged) {
    clearParentCaches(xmlTextChanged.getText());
  }

  @Nullable
  private static void clearParentCaches(final XmlElement el) {
    final TextRange textRange = el.getTextRange();
    final AntFile antFile =
      (AntFile)el.getContainingFile().getViewProvider().getPsi(AntSupport.getLanguage());
    if (antFile == null) return;
    AntElement antElement = antFile.lightFindElementAt(textRange.getStartOffset());
    while (!(antElement instanceof AntFile) && (antElement.getTextLength() < textRange.getLength() ||
                                                antElement instanceof AntOuterProjectElement)) {
      antElement = antElement.getAntParent();
    }
    antElement.clearCaches();
    AntMacroDef macrodef = PsiTreeUtil.getParentOfType(antElement, AntMacroDef.class);
    if (macrodef != null) {
      macrodef.clearCaches();
    }
  }
}
