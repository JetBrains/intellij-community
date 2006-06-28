package com.intellij.lang.ant.psi.changes;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntMacroDef;
import com.intellij.lang.ant.psi.AntPresetDef;
import com.intellij.lang.ant.psi.impl.AntOuterProjectElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.Nullable;

public class AntChangeVisitor implements XmlChangeVisitor {

  public void visitXmlAttributeSet(final XmlAttributeSet xmlAttributeSet) {
    clearParentCaches(xmlAttributeSet.getTag());
  }

  public void visitDocumentChanged(final XmlDocumentChanged xmlDocumentChanged) {
    final XmlDocument doc = xmlDocumentChanged.getDocument();
    final AntFile antFile = (AntFile)doc.getContainingFile().getViewProvider()
      .getPsi(AntSupport.getLanguage());
    if (antFile != null) {
      antFile.clearCaches();
    }
  }

  public void visitXmlElementChanged(final XmlElementChanged xmlElementChanged) {
    clearParentCaches(xmlElementChanged.getElement());
  }

  public void visitXmlTagChildAdd(final XmlTagChildAdd xmlTagChildAdd) {
    clearParentCaches(xmlTagChildAdd.getChild());
  }

  public void visitXmlTagChildChanged(final XmlTagChildChanged xmlTagChildChanged) {
    clearParentCaches(xmlTagChildChanged.getChild());
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
    final AntFile file = (AntFile)el.getContainingFile().getViewProvider().getPsi(AntSupport.getLanguage());
    if (file == null) return;
    AntElement element = file.lightFindElementAt(textRange.getStartOffset());
    while (element != null && !(element instanceof AntFile) &&
           (element.getTextLength() < textRange.getLength() || element instanceof AntOuterProjectElement)) {
      element = element.getAntParent();
    }
    if (element == null) {
      element = file;
    }
    element.clearCaches();
    AntMacroDef macrodef = PsiTreeUtil.getParentOfType(element, AntMacroDef.class);
    if (macrodef != null) {
      macrodef.clearCaches();
    }
    AntPresetDef presetdef = PsiTreeUtil.getParentOfType(element, AntPresetDef.class);
    if (presetdef != null) {
      presetdef.clearCaches();
    }
  }
}
