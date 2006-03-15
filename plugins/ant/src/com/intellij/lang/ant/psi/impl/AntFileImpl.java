package com.intellij.lang.ant.psi.impl;

import com.intellij.extapi.psi.LightPsiFileBase;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntFileImpl extends LightPsiFileBase implements AntElement {
  private AntProject myProject;
  private PsiElement[] myChildren = null;

  public AntFileImpl(final FileViewProvider viewProvider) {
    super(viewProvider, AntSupport.getLanguage());
  }

  @NotNull
  public FileType getFileType() {
    return getViewProvider().getVirtualFile().getFileType();
  }

  @NotNull
  public PsiElement[] getChildren() {
    if (myChildren == null) {
      myChildren = new PsiElement[]{ getAntParent() };
    }
    return myChildren;
  }

  public PsiElement getFirstChild() {
    return getAntProject();
  }

  public PsiElement getLastChild() {
    return getAntProject();
  }

  @Nullable
  public AntProject getAntProject() {
    if(myProject != null) return myProject;
    final XmlFile baseFile = (XmlFile)getSourceElement();
    final XmlTag tag = baseFile.getDocument().getRootTag();
    return myProject = new AntProjectImpl(this, tag);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "AntFile:" + getName();
  }

  public void clearCaches() {
    myProject = null;
    myChildren = null;
  }

  @NotNull
  public XmlElement getSourceElement() {
    return (XmlElement)getViewProvider().getPsi(StdLanguages.XML);
  }

  public AntElement getAntParent() {
    return null;
  }


  public void subtreeChanged() {
    clearCaches();
  }
}
