package com.intellij.lang.ant.psi.impl;

import com.intellij.extapi.psi.MetadataPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ant.AntLanguage;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.impl.reference.AntReferenceProvidersRegistry;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AntElementImpl extends MetadataPsiElementBase implements AntElement {

  static final XmlAttribute[] EMPTY_ATTRIBUTES = new XmlAttribute[0];

  private final AntElement myParent;
  private AntElement[] myChildren = null;
  private PsiReference[] myReferences = null;
  private XmlAttribute[] myAttributes = null;
  private Map<String, PsiElement> myProperties;
  private PsiElement[] myPropertiesArray;

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
    return (XmlElement) super.getSourceElement();
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntElement");
      final XmlElement sourceElement = getSourceElement();
      builder.append("[");
      builder.append(sourceElement.toString());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Can't rename ant element");
  }

  public AntElement getAntParent() {
    return myParent;
  }

  @SuppressWarnings({"ConstantConditions"})
  @NotNull
  public AntProject getAntProject() {
    return (AntProject) ((this instanceof AntProject) ? this : PsiTreeUtil.getParentOfType(this, AntProject.class));
  }

  public AntFileImpl getAntFile() {
    return PsiTreeUtil.getParentOfType(this, AntFileImpl.class);
  }

  public PsiElement getParent() {
    return myParent;
  }

  @NotNull
  public AntElement[] getChildren() {
    if (myChildren != null) return myChildren;
    return myChildren = getChildrenInner();
  }

  @Nullable
  public AntElement getFirstChild() {
    final AntElement[] children = getChildren();
    return (children.length == 0) ? null : children[0];
  }

  @Nullable
  public PsiElement getLastChild() {
    final PsiElement[] children = getChildren();
    return (children.length == 0) ? null : children[children.length - 1];
  }

  @Nullable
  public PsiElement getNextSibling() {
    final PsiElement parent = getAntParent();
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
    final PsiElement parent = getAntParent();
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

  public void clearCaches() {
    myReferences = null;
    myChildren = null;
    myAttributes = null;
    myProperties = null;
    myPropertiesArray = null;
  }

  @NotNull
  public XmlAttribute[] getAttributes() {
    if (myAttributes == null) {
      final XmlElement element = getSourceElement();
      if (element instanceof XmlTag) {
        myAttributes = ((XmlTag) element).getAttributes();
      } else {
        myAttributes = EMPTY_ATTRIBUTES;
      }
    }
    return myAttributes;
  }

  public void subtreeChanged() {
    final AntElement parent = getAntParent();
    clearCaches();
    if (parent != null) parent.subtreeChanged();
  }

  @Nullable
  public PsiFile findFileByName(final String name) {
    if (name == null) return null;
    AntFileImpl antFile = PsiTreeUtil.getParentOfType(this, AntFileImpl.class);
    if (antFile == null) return null;
    VirtualFile vFile = antFile.getVirtualFile();
    if (vFile == null) return null;
    vFile = vFile.getParent();
    if (vFile == null) return null;
    final File file = new File(vFile.getPath(), name);
    vFile = LocalFileSystem.getInstance().findFileByPath(file.getAbsolutePath().replace(File.separatorChar, '/'));
    if (vFile == null) return null;
    return antFile.getViewProvider().getManager().findFile(vFile);
  }

  public void setProperty(final String name, final PsiElement element) {
    if (myProperties == null) {
      myProperties = new HashMap<String, PsiElement>();
    }
    myProperties.put(name, element);
    myPropertiesArray = null;
  }

  @Nullable
  public PsiElement getProperty(final String name) {
    return (myProperties == null) ? null : myProperties.get(name);
  }

  @NotNull
  public PsiElement[] getProperties() {
    if (myProperties == null) {
      return PsiElement.EMPTY_ARRAY;
    }
    if (myPropertiesArray == null) {
      myPropertiesArray = myProperties.values().toArray(new PsiElement[myProperties.size()]);
    }
    return myPropertiesArray;
  }

  public PsiElement findElementAt(int offset) {
    final int offsetInFile = offset + getTextRange().getStartOffset();
    for (final AntElement element : getChildren()) {
      final TextRange textRange = element.getTextRange();
      if (textRange.contains(offsetInFile)) return element.findElementAt(offsetInFile - textRange.getStartOffset());
    }
    return getTextRange().contains(offsetInFile) ? this : null;
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
    final List<PsiReference> result = new ArrayList<PsiReference>();
    for (final GenericReferenceProvider provider : providers) {
      final PsiReference[] refs = provider.getReferencesByElement(this);
      for( PsiReference ref: refs) {
        result.add(ref);
      }
    }
    return myReferences = result.toArray(new PsiReference[result.size()]);
  }

  public boolean isPhysical() {
    return getSourceElement().isPhysical();
  }

  protected AntElement[] getChildrenInner() {
    return AntElement.EMPTY_ARRAY;
  }

  protected AntElement clone() {
    final AntElementImpl element = (AntElementImpl) super.clone();
    element.clearCaches();
    return element;
  }
}
