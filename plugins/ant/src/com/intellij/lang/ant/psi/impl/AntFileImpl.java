package com.intellij.lang.ant.psi.impl;

import com.intellij.extapi.psi.LightPsiFileBase;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntProperty;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
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

  public VirtualFile getVirtualFile() {
    return getSourceElement().getVirtualFile();
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
    final XmlFile baseFile = getSourceElement();
    final XmlTag tag = baseFile.getDocument().getRootTag();
    return myProject = new AntProjectImpl(this, tag);
  }

  @NotNull
  public AntProperty[] getProperties() {
    return AntProperty.EMPTY_ARRAY;
  }

  @Nullable
  public AntProperty getProperty(final String name) {
    return null;
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
  public XmlFile getSourceElement() {
    return (XmlFile)getViewProvider().getPsi(StdLanguages.XML);
  }

  public AntElement getAntParent() {
    return null;
  }

  @NotNull
  public XmlAttribute[] getAttributes() {
    return AntElementImpl.EMPTY_ATTRIBUTES;
  }

  public void subtreeChanged() {
    clearCaches();
  }
}
