package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.misc.PsiElementHashSetSpinAllocator;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class AntStructuredElementImpl extends AntElementImpl implements AntStructuredElement {

  private static final String[] EMPTY_STRING_ARRAY = new String[0];

  protected AntTypeDefinition myDefinition;
  private boolean myDefinitionCloned;
  private AntElement myIdElement;
  private AntElement myNameElement;
  @NonNls private String myNameElementAttribute;
  private Map<String, AntElement> myReferencedElements;
  private String[] myRefIdsArray;
  private int myLastFoundElementOffset = -1;
  private AntElement myLastFoundElement;
  private boolean myIsImported;

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement, @NonNls final String nameElementAttribute) {
    super(parent, sourceElement);
    myNameElementAttribute = nameElementAttribute;
    getIdElement();
    getNameElement();
  }

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement) {
    this(parent, sourceElement, "name");
  }

  public AntStructuredElementImpl(final AntElement parent,
                                  final XmlElement sourceElement,
                                  final AntTypeDefinition definition,
                                  @NonNls final String nameElementAttribute) {
    this(parent, sourceElement, nameElementAttribute);
    myDefinition = definition;
    final AntTypeId id = new AntTypeId(getSourceElement().getName());
    if (definition != null && !definition.getTypeId().equals(id)) {
      myDefinition = new AntTypeDefinitionImpl((AntTypeDefinitionImpl)myDefinition);
      myDefinition.setTypeId(id);
      myDefinitionCloned = true;
    }
  }

  public AntStructuredElementImpl(final AntElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    this(parent, sourceElement, definition, "name");
  }

  @NotNull
  public XmlTag getSourceElement() {
    return (XmlTag)super.getSourceElement();
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntStructuredElement[");
      builder.append(getSourceElement().getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public String getName() {
    if (hasNameElement()) {
      return getNameElement().getName();
    }
    if (hasIdElement()) {
      return getIdElement().getName();
    }
    return super.getName();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    if (hasNameElement()) {
      getNameElement().setName(name);
    }
    else if (hasIdElement()) {
      getIdElement().setName(name);
    }
    else {
      super.setName(name);
    }
    return this;
  }

  public PsiElement findElementAt(int offset) {
    if (offset == myLastFoundElementOffset) {
      return myLastFoundElement;
    }
    final PsiElement foundElement = super.findElementAt(offset);
    if (foundElement != null) {
      myLastFoundElement = (AntElement)foundElement;
      myLastFoundElementOffset = offset;
    }
    return foundElement;
  }

  public AntTypeDefinition getTypeDefinition() {
    AntTypeDefinition def = myDefinition;
    if (def != null) {
      final PsiNamedElement definingElement = (PsiNamedElement)def.getDefiningElement();
      if (definingElement != null && !getSourceElement().getName().equals(definingElement.getName())) {
        myDefinition = def = null;
        super.clearCaches();
      }
    }
    return def;
  }

  public void registerCustomType(final AntTypeDefinition def) {
    if (myDefinition != null) {
      if (!myDefinitionCloned) {
        myDefinition = new AntTypeDefinitionImpl((AntTypeDefinitionImpl)myDefinition);
        myDefinitionCloned = true;
      }
      myDefinition.registerNestedType(def.getTypeId(), def.getClassName());
    }
    getAntFile().registerCustomType(def);
  }

  public void unregisterCustomType(final AntTypeDefinition def) {
    if (myDefinition != null && myDefinitionCloned) {
      myDefinition.unregisterNestedType(def.getTypeId());
    }
    getAntFile().unregisterCustomType(def);
  }

  public boolean hasImportedTypeDefinition() {
    return myIsImported;
  }

  void setImportedTypeDefinition(boolean imported) {
    myIsImported = imported;
  }

  @Nullable
  public PsiFile findFileByName(final String name) {
    if (name == null) return null;
    final AntFileImpl antFile = PsiTreeUtil.getParentOfType(this, AntFileImpl.class);
    if (antFile == null) return null;
    VirtualFile vFile = antFile.getVirtualFile();
    if (vFile == null) return null;
    vFile = vFile.getParent();
    if (vFile == null) return null;
    final String fileName = computeAttributeValue(name);
    File file = new File(fileName);
    if (!file.isAbsolute()) {
      file = new File(vFile.getPath(), fileName);
      if (!file.exists()) {
        file = new File(antFile.getAntProject().getBaseDir(), fileName);
      }
    }
    vFile = LocalFileSystem.getInstance().findFileByPath(file.getAbsolutePath().replace(File.separatorChar, '/'));
    if (vFile == null) return null;
    return antFile.getViewProvider().getManager().findFile(vFile);
  }

  public String computeAttributeValue(String value) {
    final HashSet<PsiElement> set = PsiElementHashSetSpinAllocator.alloc();
    try {
      return computeAttributeValue(value, set);
    }
    finally {
      PsiElementHashSetSpinAllocator.dispose(set);
    }
  }

  public void registerRefId(final String id, AntElement element) {
    if (id == null || id.length() == 0) return;
    if (myReferencedElements == null) {
      myReferencedElements = new HashMap<String, AntElement>();
    }
    myReferencedElements.put(id, element);
  }

  public AntElement getElementByRefId(String refid) {
    refid = computeAttributeValue(refid);
    AntElement parent = this;
    do {
      if (parent instanceof AntStructuredElement) {
        AntStructuredElementImpl se = (AntStructuredElementImpl)parent;
        if (se.myReferencedElements != null) {
          final AntElement refse = se.myReferencedElements.get(refid);
          if (refse != null) {
            return refse;
          }
        }
      }
      parent = parent.getAntParent();
    }
    while (!(parent instanceof AntFile));
    return null;
  }

  @NotNull
  public String[] getRefIds() {
    if (myRefIdsArray == null) {
      if (myReferencedElements == null) {
        myRefIdsArray = EMPTY_STRING_ARRAY;
      }
      else {
        myRefIdsArray = myReferencedElements.keySet().toArray(new String[myReferencedElements.size()]);
      }
    }
    return myRefIdsArray;
  }

  public boolean hasNameElement() {
    return !isNameElement(ourNull);
  }

  public boolean hasIdElement() {
    return !isIdElement(ourNull);
  }

  public boolean isNameElement(PsiElement element) {
    return getNameElement() == element;
  }

  public boolean isIdElement(PsiElement element) {
    return getIdElement() == element;
  }

  public String getFileReferenceAttribute() {
    return null;
  }

  public boolean isTypeDefined() {
    return myDefinition != null && myDefinition.getDefiningElement() instanceof AntTypeDefImpl;
  }

  public boolean isPresetDefined() {
    return myDefinition != null && myDefinition.getClassName().startsWith(AntPresetDefImpl.ANT_PRESETDEF_NAME);
  }

  public void clearCaches() {
    super.clearCaches();
    myReferencedElements = null;
    myRefIdsArray = null;
    myIdElement = null;
    myNameElement = null;
    myLastFoundElementOffset = -1;
    myLastFoundElement = null;
  }


  public AntElement lightFindElementAt(int offset) {
    if (offset == myLastFoundElementOffset) {
      return myLastFoundElement;
    }
    return super.lightFindElementAt(offset);
  }

  public int getTextOffset() {
    if (hasNameElement()) {
      return getNameElement().getTextOffset();
    }
    if (hasIdElement()) {
      return getIdElement().getTextOffset();
    }
    return super.getTextOffset();
  }

  protected AntElement[] getChildrenInner() {
    final List<AntElement> children = new ArrayList<AntElement>();
    if (hasIdElement()) {
      children.add(getIdElement());
    }
    if (hasNameElement()) {
      children.add(getNameElement());
    }
    for (final PsiElement element : getSourceElement().getChildren()) {
      if (element instanceof XmlElement) {
        final AntElement antElement = AntElementFactory.createAntElement(this, (XmlElement)element);
        if (antElement != null) {
          children.add(antElement);
          if (antElement instanceof AntStructuredElement) {
            antElement.getChildren();
          }
        }
      }
    }
    final int count = children.size();
    return (count > 0) ? children.toArray(new AntElement[count]) : AntElement.EMPTY_ARRAY;
  }

  @NotNull
  protected AntElement getIdElement() {
    if (myIdElement == null) {
      myIdElement = ourNull;
      AntElement parent = getAntParent();
      if (parent instanceof AntStructuredElement) {
        final XmlAttribute idAttr = getSourceElement().getAttribute("id", null);
        if (idAttr != null) {
          final XmlAttributeValue valueElement = idAttr.getValueElement();
          if (valueElement != null) {
            myIdElement = new AntNameElementImpl(this, valueElement);
            AntStructuredElement se = (AntStructuredElement)parent;
            se.registerRefId(myIdElement.getName(), this);
          }
        }
      }
    }
    return myIdElement;
  }

  @NotNull
  protected AntElement getNameElement() {
    if (myNameElement == null) {
      myNameElement = ourNull;
      XmlAttribute nameAttr = getSourceElement().getAttribute(myNameElementAttribute, null);
      if (nameAttr != null) {
        final XmlAttributeValue valueElement = nameAttr.getValueElement();
        if (valueElement != null) {
          myNameElement = new AntNameElementImpl(this, valueElement);
        }
      }
    }
    return myNameElement;
  }

  @NonNls
  protected String getNameElementAttribute() {
    return myNameElementAttribute;
  }

  /**
   * Cycle-safe computation of an attribute value with resolving properties.
   *
   * @param value
   * @param elementStack
   * @return
   */
  protected String computeAttributeValue(String value, Set<PsiElement> elementStack) {
    elementStack.add(this);
    int startProp = 0;
    while ((startProp = value.indexOf("${", startProp)) >= 0) {
      int endProp = value.indexOf('}', startProp + 2);
      if (endProp <= startProp + 2) {
        startProp += 2;
        continue;
      }
      final String prop = value.substring(startProp + 2, endProp);
      final PsiElement propElement = resolveProperty(this, prop);
      if (elementStack.contains(propElement)) {
        return value;
      }
      String resolvedValue = null;
      if (propElement instanceof AntPropertyImpl) {
        final AntPropertyImpl antProperty = (AntPropertyImpl)propElement;
        resolvedValue = antProperty.getValue();
        if (resolvedValue != null) {
          resolvedValue = antProperty.computeAttributeValue(resolvedValue, elementStack);
        }
      }
      else if (propElement instanceof Property) {
        resolvedValue = ((Property)propElement).getValue();
      }
      if (resolvedValue == null) {
        startProp += 2;
      }
      else {
        if (endProp < value.length() - 1) {
          value = value.substring(0, startProp) + resolvedValue + value.substring(endProp + 1);
        }
        else {
          value = value.substring(0, startProp) + resolvedValue;
        }
      }
    }
    return value;
  }
}