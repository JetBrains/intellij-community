/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.misc.AntStringInterner;
import com.intellij.lang.ant.misc.PsiElementWithValueSetSpinAllocator;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

public class AntStructuredElementImpl extends AntElementImpl implements AntStructuredElement {

  private static final AntNameIdentifier ourNullIdentifier = new AntNameIdentifierImpl(null, null) {
    @NonNls
    public String getIdentifierName() {
      return "AntNullIdentifier";
    }

    public boolean isValid() {
      return true;
    }
  };
  protected volatile AntTypeDefinition myDefinition;
  private boolean myDefinitionCloned;
  private volatile AntNameIdentifier myIdElement;
  private volatile AntNameIdentifier myNameElement;
  @NonNls 
  private volatile String myNameElementAttribute;
  private int myLastFoundElementOffset = -1;
  private AntElement myLastFoundElement;
  private volatile boolean myIsImported;
  private StringSetHolder myComputingAttrValue;
  protected volatile boolean myInGettingChildren;
  @NonNls private static final String ANTLIB_NS_PREFIX = "antlib:";
  @NonNls private static final String ANTLIB_XML = "antlib.xml";
  private static final Pattern $$_PATTERN = Pattern.compile("\\$\\$");

  private AntStructuredElementImpl(final AntElement parent, final XmlTag sourceElement, @NonNls final String nameElementAttribute) {
    super(parent, sourceElement);
    myNameElementAttribute = AntStringInterner.intern(nameElementAttribute);
    getIdElement();
    getNameElement();
    invalidateAntlibNamespace();
  }

  public AntStructuredElementImpl(final AntElement parent, final XmlTag sourceElement) {
    this(parent, sourceElement, AntFileImpl.NAME_ATTR);
  }

  public AntStructuredElementImpl(final AntElement parent,
                                  final XmlTag sourceElement,
                                  final AntTypeDefinition definition,
                                  @NonNls final String nameElementAttribute) {
    this(parent, sourceElement, nameElementAttribute);
    myDefinition = definition;
    final AntTypeId id = new AntTypeId(sourceElement.getName());
    if (definition != null) {
      if (!definition.getTypeId().equals(id)) {
        myDefinition = new AntTypeDefinitionImpl((AntTypeDefinitionImpl)myDefinition);
        myDefinition.setTypeId(id);
        myDefinitionCloned = true;
      }
    }
    else {
      /**
       * This branch reloads of type definition in case if it could be
       * registered during invalidation of the "antlib:..." namespace
       */
      final AntFile file = getAntFile();
      if (file != null) {
        final AntTypeDefinition targetDef = file.getTargetDefinition();
        final String className = targetDef != null? targetDef.getNestedClassName(id) : null; 
        if (className != null) {
          myDefinition = file.getBaseTypeDefinition(className);
        }
      }
    }
  }

  public AntStructuredElementImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    this(parent, sourceElement, definition, AntFileImpl.NAME_ATTR);
  }

  public void acceptAntElementVisitor(@NotNull final AntElementVisitor visitor) {
    visitor.visitAntStructuredElement(this);
  }

  @NotNull
  public XmlTag getSourceElement() {
    return (XmlTag)super.getSourceElement();
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
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
      return computeAttributeValue(getNameElement().getIdentifierName());
    }
    if (hasIdElement()) {
      return computeAttributeValue(getIdElement().getIdentifierName());
    }
    final XmlTag sourceElement = getSourceElement();
    return sourceElement != null ? sourceElement.getName() : super.getName();
  }

  public PsiElement setName(@NotNull final String name) throws IncorrectOperationException {
    try {
      if (hasNameElement()) {
        getNameElement().setIdentifierName(name);
      }
      else if (hasIdElement()) {
        getIdElement().setIdentifierName(name);
      }
      return this;
    }
    finally {
      clearCaches();
    }
  }

  public boolean canRename() {
    return super.canRename() && (hasNameElement() || hasIdElement());
  }

  public PsiElement findElementAt(int offset) {
    synchronized (PsiLock.LOCK) {
      if (offset != myLastFoundElementOffset || (myLastFoundElement != null && !myLastFoundElement.isValid())) {
        final PsiElement foundElement = super.findElementAt(offset);
        if (foundElement == null) {
          return null;
        }
        myLastFoundElement = (AntElement)foundElement;
        myLastFoundElementOffset = offset;
      }
      return myLastFoundElement;
    }
  }

  public AntTypeDefinition getTypeDefinition() {
    synchronized (PsiLock.LOCK) {
      if (myDefinition != null && !myDefinitionCloned && myDefinition.isOutdated()) {
        final AntTypeDefinition currentDef = getAntFile().getBaseTypeDefinition(myDefinition.getClassName());
        if (currentDef != null) {
          myDefinition = currentDef;
        }
      }
      return myDefinition;
    }
  }

  public void registerCustomType(final AntTypeDefinition def) {
    synchronized (PsiLock.LOCK) {
      AntTypeDefinition definition = getTypeDefinition();
      if (definition != null) {
        if (!myDefinitionCloned) {
          definition = new AntTypeDefinitionImpl((AntTypeDefinitionImpl)definition);
          myDefinition = definition;
          myDefinitionCloned = true;
        }
        definition.registerNestedType(def.getTypeId(), def.getClassName());
      }
      getAntFile().registerCustomType(def);
    }
  }

  public void unregisterCustomType(final AntTypeDefinition def) {
    synchronized (PsiLock.LOCK) {
      if (myDefinition != null && myDefinitionCloned) {
        final AntTypeId typeId = def.getTypeId();
        // for the same typeId there might be different classes registered
        // so unregister only that type that is really registered as a nested within this element
        final String registeredClassName = myDefinition.getNestedClassName(typeId);
        if (registeredClassName != null && registeredClassName.equals(def.getClassName())) {
          myDefinition.unregisterNestedType(typeId);
        }
      }
      getAntFile().unregisterCustomType(def);
    }
  }

  public boolean hasImportedTypeDefinition() {
    return myIsImported;
  }

  void setImportedTypeDefinition(boolean imported) {
    myIsImported = imported;
  }

  @Nullable
  public PsiFile findFileByName(final String name) {
    return findFileByName(name, "");
  }

  @Nullable
  public PsiFile findFileByName(final String name, @Nullable final String baseDir) {
    if (name == null) {
      return null;
    }
    final AntFile antFile = getAntFile();
    if (antFile == null) {
      return null;
    }
    VirtualFile vFile = antFile.getContainingPath();
    if (vFile == null) {
      return null;
    }
    String projectPath = vFile.getPath();
    String dir = baseDir;
    if (dir == null) {
      final AntProject project = antFile.getAntProject();
      dir = project == null ? null : project.getBaseDir();
    }
    if (dir != null && dir.length() > 0) {
      projectPath = new File(projectPath, dir).getAbsolutePath();
    }
    final String fileName = computeAttributeValue(name);
    if (fileName == null) {
      return null;
    }
    File file = new File(fileName);
    if (!file.isAbsolute()) {
      file = new File(projectPath, fileName);
    }
    vFile = LocalFileSystem.getInstance().findFileByPath(file.getAbsolutePath().replace(File.separatorChar, '/'));
    if (vFile == null) {
      return null;
    }
    return antFile.getViewProvider().getManager().findFile(vFile);
  }

  @Nullable
  public String computeAttributeValue(final String value) {
    if (value == null || value.indexOf('$') < 0) {
      return value; // optimization
    }
    synchronized (PsiLock.LOCK) {
      if (value != null) {
        if (myComputingAttrValue == null || !myComputingAttrValue.contains(value)) {
          try {
            if (myComputingAttrValue == null) {
              myComputingAttrValue = new StringSetHolder();
            }
            final AntConfigurationBase antConfig = AntConfigurationBase.getInstance(getProject());
            myComputingAttrValue.add(value);
            try {
              final Set<Pair<PsiElement,String>> set = PsiElementWithValueSetSpinAllocator.alloc();
              try {
                return computeAttributeValue(value, set, antConfig);
              }
              finally {
                PsiElementWithValueSetSpinAllocator.dispose(set);
              }
            }
            catch (SpinAllocator.AllocatorExhaustedException e) {
              return computeAttributeValue(value, new HashSet<Pair<PsiElement, String>>(), antConfig);
            }
          }
          finally {
            myComputingAttrValue.remove(value);
            if (myComputingAttrValue.size() == 0) {
              myComputingAttrValue.dispose();
              myComputingAttrValue = null;
            }
          }
        }
      }
      return null;
    }
  }

  public boolean hasNameElement() {
    return getNameElement() != ourNullIdentifier;
  }

  public boolean hasIdElement() {
    return getIdElement() != ourNullIdentifier;
  }

  @NotNull
  public List<String> getFileReferenceAttributes() {
    final AntTypeDefinition typeDef = getTypeDefinition();
    if (typeDef == null) {
      return Collections.emptyList();
    }
    final String[] attribs = typeDef.getAttributes();
    if (attribs.length == 0) {
      return Collections.emptyList();
    }
    final List<String> fileRefAttributes = new ArrayList<String>(attribs.length);
    for (String attrib : attribs) {
      if (typeDef.getAttributeType(attrib) == AntAttributeType.FILE) {
        fileRefAttributes.add(attrib);
      }
    }
    return fileRefAttributes;
  }

  public boolean isTypeDefined() {
    final AntTypeDefinition def = getTypeDefinition();
    return def != null && def.getDefiningElement() instanceof AntTypeDefImpl;
  }

  public boolean isPresetDefined() {
    final AntTypeDefinition def = getTypeDefinition();
    return def != null && def.getClassName().startsWith(AntPresetDefImpl.ANT_PRESETDEF_NAME);
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      super.clearCaches();
      myIdElement = null;
      myNameElement = null;
      myLastFoundElementOffset = -1;
      myLastFoundElement = null;
      myLastProcessedChildren = null;
      invalidateAntlibNamespace();
    }
  }

  public AntElement lightFindElementAt(int offset) {
    synchronized (PsiLock.LOCK) {
      if (offset == myLastFoundElementOffset) {
        return myLastFoundElement;
      }
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
    synchronized (PsiLock.LOCK) {
      if (!myInGettingChildren) {
        myInGettingChildren = true;
        try {
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
                /*
                if (antElement instanceof AntStructuredElement) {
                  antElement.getChildren();
                }
                */
              }
            }
          }
          final int count = children.size();
          return count > 0 ? children.toArray(new AntElement[count]) : AntElement.EMPTY_ARRAY;
        }
        finally {
          myInGettingChildren = false;
        }
      }
    }
    return AntElement.EMPTY_ARRAY;
  }

  @NotNull
  public AntElement[] getChildren() {
    synchronized (PsiLock.LOCK) {
      return fixUndefinedElements(super.getChildren());
    }
  }

  private AntElement[] myLastProcessedChildren;

  private AntElement[] fixUndefinedElements(final AntElement[] elements) {
    if (myLastProcessedChildren == elements) {
      return elements;
    }
    myLastProcessedChildren = elements;
    if (isValid()) {
      for (int i = 0; i < elements.length; i++) {
        final AntElement element = elements[i];
        if (element instanceof AntStructuredElement && element.isValid()) {
          AntStructuredElement se = (AntStructuredElement)element;
          if (se.getTypeDefinition() == null) {
            final XmlTag sourceElement = se.getSourceElement();
            se = (AntStructuredElement)AntElementFactory.createAntElement(this, sourceElement);
            if (se != null && se.getTypeDefinition() != null) {
              elements[i] = se;
            }
          }
        }
      }
    }
    return elements;
  }

  @NotNull
  private AntNameIdentifier getIdElement() {
    synchronized (PsiLock.LOCK) {
      if (myIdElement == null) {
        myIdElement = ourNullIdentifier;
        final XmlTag se = getSourceElement();
        if (se.isValid()) {
          final XmlAttribute idAttr = se.getAttribute(AntFileImpl.ID_ATTR, null);
          if (idAttr != null) {
            final XmlAttributeValue valueElement = idAttr.getValueElement();
            if (valueElement != null) {
              final AntNameIdentifierImpl identifier = new AntNameIdentifierImpl(this, valueElement);
              myIdElement = identifier;
              getAntProject().registerRefId(identifier.getIdentifierName(), this);
            }
          }
        }
      }
      return myIdElement;
    }
  }

  @NotNull
  private AntNameIdentifier getNameElement() {
    synchronized (PsiLock.LOCK) {
      if (myNameElement == null) {
        myNameElement = ourNullIdentifier;
        final XmlTag se = getSourceElement();
        if (se.isValid()) {
          final XmlAttribute nameAttr = se.getAttribute(myNameElementAttribute, null);
          if (nameAttr != null) {
            final XmlAttributeValue valueElement = nameAttr.getValueElement();
            if (valueElement != null) {
              myNameElement = new AntNameIdentifierImpl(this, valueElement);
            }
          }
        }
      }
      return myNameElement;
    }
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
   * @param antConfig
   * @return
   */
  private String computeAttributeValue(String value, final Set<Pair<PsiElement, String>> elementStack, final AntConfigurationBase antConfig) {
    final Pair<PsiElement, String> resolveStackEntry = new Pair<PsiElement, String>(this, value);
    elementStack.add(resolveStackEntry);
    try {
      int startProp = 0;
      final AntFile self = getAntFile();
      final AntFile antFile = null/*antConfig.getEffectiveContextFile(self)*/;
      while ((startProp = value.indexOf("${", startProp)) >= 0) {
        if (startProp > 0 && value.charAt(startProp - 1) == '$') {
          // the '$' is escaped
          startProp += 2;
          continue;
        }
        final int endProp = value.indexOf('}', startProp + 2);
        if (endProp <= startProp + 2) {
          startProp += 2;
          continue;
        }
        final String prop = value.substring(startProp + 2, endProp);
        final Ref<String> resolvedValueRef = new Ref<String>(null);
        final Ref<Boolean> shouldReturnOriginalValue = new Ref<Boolean>(Boolean.FALSE);
        antFile.processAllProperties(prop, new Processor<AntProperty>() {
          public boolean process(AntProperty antProperty) {
            final String resolvedValue = antProperty.getValue(prop);
            if (resolvedValue == null) {
              return true;
            }
            if (elementStack.contains(new Pair<PsiElement, String>(antProperty, resolvedValue))) {
              shouldReturnOriginalValue.set(Boolean.TRUE);
            }
            else {
              resolvedValueRef.set(((AntStructuredElementImpl)antProperty).computeAttributeValue(resolvedValue, elementStack, antConfig));
            }
            return false;
          }
        });
        if (shouldReturnOriginalValue.get()) {
          return value; // prevent cycles
        }
        if (resolvedValueRef.get() == null) {
          startProp += 2;
        }
        else {
          if (resolvedValueRef.get().equals(value) /*prevent tail recursion*/) {
            return value;
          }
          final StringBuilder builder = StringBuilderSpinAllocator.alloc();
          try {
            builder.append(value, 0, startProp);
            builder.append(resolvedValueRef.get());
            if (endProp < value.length() - 1) {
              builder.append(value, endProp + 1, value.length());
            }
            final String substituted = builder.toString();
            if (!substituted.equals(value)) {
              value = substituted;
            }
            else {
              startProp += 2;
            }
          }
          finally {
            StringBuilderSpinAllocator.dispose(builder);
          }
        }
      }
      if (value.indexOf("$$") >= 0) {
        return $$_PATTERN.matcher(value).replaceAll("\\$");
      }
      return value;
    }
    finally {
      elementStack.remove(resolveStackEntry);
    }
  }

  private String getNamespace() {
    return getSourceElement().getNamespace();
  }

  private void invalidateAntlibNamespace() {
    final AntFile file = getAntFile();
    if (file == null) return;
    final ClassLoader loader = file.getClassLoader();
    if (loader == null) return;
    final String ns = getNamespace();
    if (!ns.startsWith(ANTLIB_NS_PREFIX)) return;
    final AntElement parent = getAntParent();
    if (!(this instanceof AntProject)) {
      if (parent instanceof AntStructuredElementImpl && ns.equals(((AntStructuredElementImpl)parent).getNamespace())) return;
    }

    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(ns.substring(ANTLIB_NS_PREFIX.length()).replace('.', '/'));
      builder.append('/');
      builder.append(ANTLIB_XML);
      final InputStream antlibStream = loader.getResourceAsStream(builder.toString());
      if (antlibStream != null) {
        AntTypeDefImpl.loadAntlibStream(antlibStream, this, ns);
      }
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }
  
  private static final class StringSetHolder {
    @NotNull
    private Set<String> mySet;
    private boolean myShouldDispose = true;

    public StringSetHolder() {
      try {
        mySet = StringSetSpinAllocator.alloc();
      }
      catch (SpinAllocator.AllocatorExhaustedException e) {
        mySet = new HashSet<String>();
        myShouldDispose = false;
      }
    }
    
    public boolean contains(String str) {
      return mySet.contains(str);
    }

    public int size() {
      return mySet.size();
    }

    public boolean add(final String s) {
      return mySet.add(s);
    }

    public boolean remove(final String s) {
      return mySet.remove(s);
    }
    
    public void dispose()  {
      if (myShouldDispose) {
        StringSetSpinAllocator.dispose(mySet);
      }
    }
  }
}
