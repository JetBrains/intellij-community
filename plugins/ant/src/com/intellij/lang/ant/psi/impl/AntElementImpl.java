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

  private static final PsiElement[] EMPTY_CHILDREN = new PsiElement[0];
  private final PsiElement myParent;

  protected PsiElement[] myChildren;

  public AntElementImpl(final PsiElement parent, final PsiElement sourceElement) {
    super(sourceElement);
    myParent = parent;
  }

  @NotNull
  public Language getLanguage() {
    return AntSupport.getLanguage();
  }

  @NotNull
  public XmlTag getSourceTag() {
    return (XmlTag)getSourceElement();
  }


  public PsiElement getParent() {
    return myParent;
  }

  @NotNull
  public PsiElement[] getChildren() {
    if( myChildren != null ) {
      return myChildren;
    }
    return EMPTY_CHILDREN;
  }

  @Nullable
  public PsiElement getFirstChild() {
    final PsiElement[] children = getChildren();
    return (children.length == 0) ? null : children[0];
  }

  @Nullable
  public PsiElement getLastChild() {
    final PsiElement[] children = getChildren();
    return (children.length == 0) ? null : children[children.length - 1];
  }

  @Nullable
  public PsiElement getNextSibling() {
    final PsiElement parent = getParent();
    if (parent != null) {
      final PsiElement[] thisLevelElements = parent.getChildren();
      PsiElement thisElement = null;
      for (PsiElement element : thisLevelElements) {
        if (thisElement != null) {
          return element;
        }
        if (element == this) {
          thisElement = element;
        }
      }
    }
    return null;
  }

  @Nullable
  public PsiElement getPrevSibling() {
    PsiElement prev = null;
    final PsiElement parent = getParent();
    if (parent != null) {
      final PsiElement[] thisLevelElements = parent.getChildren();
      for (PsiElement element : thisLevelElements) {
        if (element == this) {
          break;
        }
        prev = element;
      }
    }
    return prev;
  }

  public PsiElement findElementAt(int offset) {
    return null;
  }

  public ASTNode getNode() {
    return null;
  }
}
