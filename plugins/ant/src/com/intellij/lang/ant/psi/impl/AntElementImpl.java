package com.intellij.lang.ant.psi.impl;

import com.intellij.extapi.psi.MetadataPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.AntLanguage;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.misc.PsiReferenceListSpinAllocator;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntElementVisitor;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.impl.reference.AntReferenceProvidersRegistry;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class AntElementImpl extends MetadataPsiElementBase implements AntElement {

  protected static final AntElement ourNull = new AntElementImpl(null, null) {
    @NonNls
    public String getName() {
      return "AntNull";
    }

    public boolean isValid() {
      return true;
    }
  };

  private final AntElement myParent;
  private volatile AntElement[] myChildren;
  private volatile PsiReference[] myReferences;
  private volatile PsiElement myPrev;
  private volatile PsiElement myNext;

  public AntElementImpl(final AntElement parent, final XmlElement sourceElement) {
    super(sourceElement);
    myParent = parent;
  }

  @NotNull
  public AntLanguage getLanguage() {
    return AntSupport.getLanguage();
  }

  @NotNull
  public XmlElement getSourceElement() {
    return (XmlElement)super.getSourceElement();
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntElement[");
      builder.append(this == ourNull ? "null" : getSourceElement().toString());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @Nullable
  public AntElement getAntParent() {
    return myParent;
  }

  public AntProject getAntProject() {
    return (AntProject)(this instanceof AntProject ? this : PsiTreeUtil.getParentOfType(this, AntProject.class, true, true));
  }

  public AntFile getAntFile() {
    return PsiTreeUtil.getParentOfType(this, AntFile.class, true, true);
  }

  public PsiElement getParent() {
    return myParent;
  }

  @NotNull
  public AntElement[] getChildren() {
    synchronized (PsiLock.LOCK) {
      if (myChildren == null) {
        final AntFileImpl antFile = (AntFileImpl)getAntFile();
        if (antFile != null) {
          antFile.buildPropertiesIfNeeded();
        }
      }
      if (myChildren != null) {
        return myChildren;
      }
      return myChildren = getChildrenInner();
    }
  }

  @Nullable
  public AntElement getFirstChild() {
    final AntElement[] children = getChildren();
    return children.length == 0 ? null : children[0];
  }

  @Nullable
  public PsiElement getLastChild() {
    final PsiElement[] children = getChildren();
    return children.length == 0 ? null : children[children.length - 1];
  }

  @Nullable
  public PsiElement getNextSibling() {
    if (myNext == null) {
      final PsiElement parent = getAntParent();
      if (parent != null) {
        PsiElement temp = null;
        for (final PsiElement element : parent.getChildren()) {
          if (temp != null) {
            myNext = element;
            break;
          }
          if (element == this) {
            temp = element;
          }
        }
      }
    }
    return myNext;
  }

  @Nullable
  public PsiElement getPrevSibling() {
    if (myPrev == null) {
      PsiElement prev = null;
      final PsiElement parent = getAntParent();
      if (parent != null) {
        for (final PsiElement element : parent.getChildren()) {
          if (element == this) {
            break;
          }
          prev = element;
        }
      }
      myPrev = prev;
    }
    return myPrev;
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      myChildren = null;
      myReferences = null;
      myPrev = null;
      myNext = null;
      incModificationCount();
    }
  }

  public void incModificationCount() {
    getAntFile().incModificationCount();
  }

  @Nullable
  public AntElement lightFindElementAt(int offset) {
    synchronized (PsiLock.LOCK) {
      if (myChildren == null) return this;
      final TextRange ownRange = getTextRange();
      final int offsetInFile = offset + ownRange.getStartOffset();
      for (final AntElement element : getChildren()) {
        final TextRange textRange = element.getTextRange();
        if (textRange.contains(offsetInFile)) {
          return element.lightFindElementAt(offsetInFile - textRange.getStartOffset());
        }
      }
      return ownRange.contains(offsetInFile) ? this : null;
    }
  }

  public PsiElement findElementAt(int offset) {
    if (!isValid()) {
      return null;
    }
    final TextRange ownRange = getTextRange();
    final int offsetInFile = offset + ownRange.getStartOffset();
    
    for (final AntElement element : getChildren()) {
      final TextRange textRange = element.getTextRange();
      if (textRange.contains(offsetInFile)) {
        PsiElement elementAt = element.findElementAt(offsetInFile - textRange.getStartOffset());
        assert elementAt == null || elementAt.isValid() : element + ": " + offset;
        return elementAt;
      }
    }
    return ownRange.contains(offsetInFile)? this : null;
  }

  public ASTNode getNode() {
    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    if (myReferences != null) {
      return myReferences;
    }
    final GenericReferenceProvider[] providers = AntReferenceProvidersRegistry.getProvidersByElement(this);
    final List<PsiReference> result = PsiReferenceListSpinAllocator.alloc();
    try {
      for (final GenericReferenceProvider provider : providers) {
        final PsiReference[] refs = provider.getReferencesByElement(this);
        result.addAll(Arrays.asList(refs));
      }
      return myReferences = result.toArray(new PsiReference[result.size()]);
    }
    finally {
      PsiReferenceListSpinAllocator.dispose(result);
    }
  }

  public AntElementRole getRole() {
    return AntElementRole.NULL_ROLE;
  }

  public boolean canRename() {
    return isPhysical();
  }

  public boolean isPhysical() {
    return getSourceElement().isPhysical();
  }

  public boolean isValid() {
    return getSourceElement().isValid();
  }

  
  PsiManager myCachedManager;
  public PsiManager getManager() {
    PsiManager manager = myCachedManager;
    if (manager == null) {
      manager = getSourceElement().getManager();
      myCachedManager = manager;
    }
    return manager;
  }

  protected AntElement[] getChildrenInner() {
    return AntElement.EMPTY_ARRAY;
  }

  public void acceptAntElementVisitor(@NotNull final AntElementVisitor visitor) {
    visitor.visitAntElement(this);
  }

  protected AntElement clone() {
    final AntElementImpl element = (AntElementImpl)super.clone();
    element.clearCaches();
    return element;
  }

}
