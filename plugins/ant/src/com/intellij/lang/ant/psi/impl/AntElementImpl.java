package com.intellij.lang.ant.psi.impl;

import com.intellij.extapi.psi.MetadataPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AntElementImpl extends MetadataPsiElementBase implements AntElement {

  public AntElementImpl(final PsiElement sourceElement) {
    super(sourceElement);
  }

  @NotNull
  public Language getLanguage() {
    return AntSupport.getLanguage();
  }

  @NotNull
  public XmlTag getSourceTag() {
    return (XmlTag)getSourceElement();
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
    return null;
  }
}
