package com.intellij.lang.ant.psi;

import com.intellij.extapi.psi.LightPsiFileBase;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.impl.AntProjectImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class AntFile extends LightPsiFileBase {

  private AntProject myProject;
  private PsiElement[] myChildren = null;

  public AntFile(final FileViewProvider viewProvider) {
    super(viewProvider, AntSupport.getLanguage());
  }

  @NotNull
  public FileType getFileType() {
    return AntSupport.getFileType();
  }

  @NotNull
  public PsiElement[] getChildren() {
    if (myChildren == null) {
      createFileStructure();
      myChildren = new PsiElement[]{myProject};
    }
    return myChildren;
  }

  public PsiElement getFirstChild() {
    final PsiElement[] psiElements = getChildren();
    return (psiElements.length > 0) ? psiElements[0] : null;
  }

  public PsiElement getLastChild() {
    final PsiElement[] psiElements = getChildren();
    return (psiElements.length > 0) ? psiElements[psiElements.length - 1] : null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "AntFile:" + getName();
  }

  public void clearCaches() {
    myProject = null;
    myChildren = null;
  }

  private void createFileStructure() {
    if (myProject == null) {
      final FileViewProvider viewProvider = getViewProvider();
      final XmlFile baseFile = (XmlFile)viewProvider.getPsi(viewProvider.getBaseLanguage());
      final XmlTag tag = baseFile.getDocument().getRootTag();
      myProject = new AntProjectImpl(this, tag);
    }
  }
}
