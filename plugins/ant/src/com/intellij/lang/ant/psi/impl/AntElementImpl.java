package com.intellij.lang.ant.psi.impl;

import com.intellij.extapi.psi.MetadataPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.AntLanguage;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.misc.PsiElementSetSpinAllocator;
import com.intellij.lang.ant.misc.PsiReferenceListSpinAllocator;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.impl.reference.AntReferenceProvidersRegistry;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AntElementImpl extends MetadataPsiElementBase implements AntElement {

  protected static AntElement ourNull = new AntElementImpl(null, null) {
    @NonNls
    public String getName() {
      return "AntNull";
    }

    public boolean isValid() {
      return true;
    }
  };

  private final AntElement myParent;
  private AntElement[] myChildren;
  private PsiReference[] myReferences;
  private PsiElement myPrev;
  private PsiElement myNext;
  protected Map<String, AntProperty> myProperties;
  private AntProperty[] myPropertiesArray;

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
      builder.append((this == ourNull) ? "null" : getSourceElement().toString());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Can't rename ant element");
  }

  @Nullable
  public AntElement getAntParent() {
    return myParent;
  }

  public AntProject getAntProject() {
    return (AntProject)((this instanceof AntProject) ? this : PsiTreeUtil.getParentOfType(this, AntProject.class));
  }

  public AntFile getAntFile() {
    return PsiTreeUtil.getParentOfType(this, AntFile.class);
  }

  public PsiElement getParent() {
    return myParent;
  }

  @NotNull
  public AntElement[] getChildren() {
    synchronized (PsiLock.LOCK) {
      if (myChildren != null) return myChildren;
      return myChildren = getChildrenInner();
    }
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
    }
    myReferences = null;
    myProperties = null;
    myPropertiesArray = null;
    myPrev = null;
    myNext = null;
  }

  @Nullable
  public AntProperty getProperty(final String name) {
    return (myProperties == null) ? null : myProperties.get(name);
  }

  public void setProperty(final String name, final AntProperty element) {
    if (name == null || name.length() == 0) return;
    if (myProperties == null) {
      myProperties = new HashMap<String, AntProperty>();
    }
    myProperties.put(name, element);
    myPropertiesArray = null;
  }

  @NotNull
  public AntProperty[] getProperties() {
    if (myProperties == null) {
      return AntProperty.EMPTY_ARRAY;
    }
    if (myPropertiesArray == null) {
      myPropertiesArray = myProperties.values().toArray(new AntProperty[myProperties.size()]);
    }
    return myPropertiesArray;
  }

  @Nullable
  public AntElement lightFindElementAt(int offset) {
    synchronized (PsiLock.LOCK) {
      if (myChildren == null) return this;
      final int offsetInFile = offset + getTextRange().getStartOffset();
      for (final AntElement element : getChildren()) {
        final TextRange textRange = element.getTextRange();
        if (textRange.contains(offsetInFile)) {
          return element.lightFindElementAt(offsetInFile - textRange.getStartOffset());
        }
      }
      return getTextRange().contains(offsetInFile) ? this : null;
    }
  }

  public PsiElement findElementAt(int offset) {
    final int offsetInFile = offset + getTextRange().getStartOffset();
    for (final AntElement element : getChildren()) {
      final TextRange textRange = element.getTextRange();
      if (textRange.contains(offsetInFile)) {
        return element.findElementAt(offsetInFile - textRange.getStartOffset());
      }
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
    final List<PsiReference> result = PsiReferenceListSpinAllocator.alloc();
    try {
      for (final GenericReferenceProvider provider : providers) {
        final PsiReference[] refs = provider.getReferencesByElement(this);
        for (PsiReference ref : refs) {
          result.add(ref);
        }
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

  public PsiManager getManager() {
    return getSourceElement().getManager();
  }

  protected AntElement[] getChildrenInner() {
    return AntElement.EMPTY_ARRAY;
  }

  protected AntElement clone() {
    final AntElementImpl element = (AntElementImpl)super.clone();
    element.clearCaches();
    return element;
  }

  @Nullable
  public static PsiElement resolveProperty(@NotNull final AntElement element, final String propName) {
    if (propName == null) return null;
    PsiElement result;
    AntElement temp = element;
    while (temp != null) {
      result = temp.getProperty(propName);
      if (result != null) {
        return result;
      }
      temp = temp.getAntParent();
    }
    final Set<PsiElement> projectsStack = PsiElementSetSpinAllocator.alloc();
    try {
      result = resolvePropertyInProject(element.getAntProject(), propName, projectsStack);
      if (result != null) return result;
    }
    finally {
      PsiElementSetSpinAllocator.dispose(projectsStack);
    }
    final AntTarget target = PsiTreeUtil.getParentOfType(element, AntTarget.class);
    if (target != null) {
      final Set<PsiElement> targetsStack = PsiElementSetSpinAllocator.alloc();
      try {
        result = resolveTargetProperty(target, propName, targetsStack);
      }
      finally {
        PsiElementSetSpinAllocator.dispose(targetsStack);
      }
    }
    return result;
  }

  @Nullable
  private static PsiElement resolvePropertyInProject(final AntProject project, final String propName, final Set<PsiElement> stack) {
    PsiElement result = null;
    if (!stack.contains(project)) {
      result = resolvePropertyInElement(project, propName);
      if (result == null) {
        stack.add(project);
        for (final AntFile file : project.getImportedFiles()) {
          final AntProject importedProject = file.getAntProject();
          importedProject.getChildren();
          if ((result = resolvePropertyInProject(importedProject, propName, stack)) != null) break;
        }
        stack.remove(project);
      }
    }
    return result;
  }

  @Nullable
  private static PsiElement resolveTargetProperty(final AntTarget target, final String propName, final Set<PsiElement> stack) {
    PsiElement result = null;
    if (!stack.contains(target)) {
      result = resolvePropertyInElement(target, propName);
      if (result == null) {
        stack.add(target);
        for (AntTarget dependie : target.getDependsTargets()) {
          if ((result = resolveTargetProperty(dependie, propName, stack)) != null) break;
        }
        stack.remove(target);
      }
    }
    return result;
  }

  @Nullable
  private static PsiElement resolvePropertyInElement(final AntStructuredElement element, final String propName) {
    PsiElement result = element.getProperty(propName);
    if (result == null) {
      result = resolvePropertyInChildishPropertyFiles(element, propName);
    }
    return result;
  }

  @Nullable
  private static PsiElement resolvePropertyInChildishPropertyFiles(final AntStructuredElement element, final String propName) {
    PsiElement result = null;
    for (PsiElement child : element.getChildren()) {
      if (child instanceof AntProperty) {
        final AntProperty prop = (AntProperty)child;
        final PropertiesFile propFile = prop.getPropertiesFile();
        if (propFile != null) {
          String prefix = prop.getPrefix();
          if (prefix != null && !prefix.endsWith(".")) {
            prefix += '.';
          }
          final String key = (prefix == null) ? propName : prefix + propName;
          final Property property = propFile.findPropertyByKey(key);
          if (property != null) {
            result = property;
            break;
          }
        }
      }
    }
    return result;
  }
}
