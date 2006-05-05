package com.intellij.lang.ant.psi.impl;

import com.intellij.extapi.psi.LightPsiFileBase;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
      final AntProject project = getAntProject();
      myChildren = new PsiElement[]{project};
    }
    return myChildren;
  }

  public PsiElement getFirstChild() {
    return getAntProject();
  }

  public PsiElement getLastChild() {
    return getAntProject();
  }

  @NotNull
  public AntProject getAntProject() {
    if (myProject != null) return myProject;
    final XmlFile baseFile = getSourceElement();
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

  public void subtreeChanged() {
  }

  @NotNull
  public XmlFile getSourceElement() {
    return (XmlFile) getViewProvider().getPsi(StdLanguages.XML);
  }

  public AntElement getAntParent() {
    return null;
  }

  @Nullable
  public PsiFile findFileByName(final String name) {
    return null;
  }

  public void setProperty(final String name, final PsiElement element) {
  }

  @Nullable
  public PsiElement getProperty(final String name) {
    return null;
  }

  @NotNull
  public PsiElement[] getProperties() {
    return PsiElement.EMPTY_ARRAY;
  }
}
