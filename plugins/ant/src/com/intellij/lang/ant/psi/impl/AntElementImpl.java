package com.intellij.lang.ant.psi.impl;

import com.intellij.extapi.psi.MetadataPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public abstract class AntElementImpl extends MetadataPsiElementBase implements AntElement {

  private static final AntElement[] EMPTY_CHILDREN = new AntElement[0];
  private final PsiElement myParent;
  private AntElement[] myChildren;

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
    if (myChildren == null) {
      myChildren = EMPTY_CHILDREN;
      ArrayList<AntElement> children = null;
      final XmlTag tag = getSourceTag();
      final XmlTag[] tags = tag.getSubTags();
      for (XmlTag subtag : tags) {
        AntElement child = parseSubTag(subtag);
        if (child != null) {
          if (children == null) {
            children = new ArrayList<AntElement>();
          }
          children.add(child);
        }
      }
      if (children != null) {
        myChildren = children.toArray(new AntElement[children.size()]);
      }
    }
    return myChildren;
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
    final TextRange textRange = getTextRange();
    if (textRange.getStartOffset() <= offset && textRange.getEndOffset() >= offset) {
      final PsiElement[] children = getChildren();
      for (PsiElement child : children) {
        final PsiElement psiElement = child.findElementAt(offset);
        if (psiElement != null) {
          return psiElement;
        }
      }
      return this;
    }
    return null;
  }

  public ASTNode getNode() {
    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return new PsiReference[]{new PsiReference() {

      public PsiElement getElement() {
        return AntElementImpl.this;
      }

      public TextRange getRangeInElement() {
        return getTextRange();
      }

      @Nullable
      public PsiElement resolve() {
        return null;
      }

      public String getCanonicalText() {
        return getText();
      }

      public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        return null;
      }

      public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
        return null;
      }

      public boolean isReferenceTo(PsiElement element) {
        return false;
      }

      public Object[] getVariants() {
        return new Object[0];
      }

      public boolean isSoft() {
        return false;
      }
    }};
  }

  protected abstract AntElement parseSubTag(final XmlTag tag);

}
