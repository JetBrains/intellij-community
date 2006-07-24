package com.intellij.lang.ant.psi.changes;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.impl.AntOuterProjectElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.xml.XmlChangeVisitor;
import com.intellij.pom.xml.events.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;

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
      updateBuildFile(antFile);
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
    AntTypeDef typeDef = PsiTreeUtil.getParentOfType(element, AntTypeDef.class);
    if (typeDef != null) {
      typeDef.clearCaches();
    }
    updateBuildFile(file);
  }

  private static void updateBuildFile(final AntFile file) {
    final AntConfiguration antConfiguration = AntConfiguration.getInstance(file.getProject());
    for (final AntBuildFile buildFile : antConfiguration.getBuildFiles()) {
      if (file.equals(buildFile.getAntFile())) {
        antConfiguration.updateBuildFile(buildFile);
        break;
      }
    }
  }
}
