package com.intellij.lang.ant.psi.impl;

import com.intellij.extapi.psi.MetadataPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntElementImpl extends MetadataPsiElementBase implements AntElement {

  protected final AntFile myFile;
  private AntASTNode myNode = null;

  public AntElementImpl(final PsiElement sourceElement, final AntFile file) {
    super(sourceElement);
    myFile = file;
  }

  @NotNull
  public Language getLanguage() {
    return AntSupport.getLanguage();
  }

  @NotNull
  public PsiElement[] getChildren() {
    return new PsiElement[0];
  }

  public PsiElement getParent() {
    return null;
  }

  @Nullable
  public PsiElement getFirstChild() {
    return null;
  }

  @Nullable
  public PsiElement getLastChild() {
    return null;
  }

  @Nullable
  public PsiElement getNextSibling() {
    return null;
  }

  @Nullable
  public PsiElement getPrevSibling() {
    return null;
  }

  public PsiElement findElementAt(int offset) {
    return null;
  }

  public ASTNode getNode() {
    if (myNode == null) {
      final ASTNode sourceNode = getSourceElement().getNode();
      myNode = new AntASTNode(sourceNode, this, myFile);
      myFile.registerAntNode(myNode);
    }
    return myNode;
  }
}
