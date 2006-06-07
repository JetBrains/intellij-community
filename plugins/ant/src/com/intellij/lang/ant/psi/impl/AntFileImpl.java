package com.intellij.lang.ant.psi.impl;

import com.intellij.extapi.psi.LightPsiFileBase;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLock;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings({"UseOfObsoleteCollectionType"})
public class AntFileImpl extends LightPsiFileBase implements AntFile {
  private AntProject myProject;
  private AntElement myPrologElement;
  private AntElement myEpilogueElement;
  private PsiElement[] myChildren;
  private Project myAntProject;
  /**
   * Map of class names to task definitions.
   */
  private Map<String, AntTypeDefinition> myTypeDefinitions;
  private AntTypeDefinition[] myTypeDefinitionArray;
  private AntTypeDefinition myTargetDefinition;
  /**
   * Set of classnames of custom definitions.
   */
  private Set<String> myCustomDefinitions;

  public AntFileImpl(final FileViewProvider viewProvider) {
    super(viewProvider, AntSupport.getLanguage());
  }

  @NotNull
  public FileType getFileType() {
    return getViewProvider().getVirtualFile().getFileType();
  }

  public VirtualFile getVirtualFile() {
    return getSourceElement().getVirtualFile();
  }

  @NotNull
  public PsiElement[] getChildren() {
    synchronized (PsiLock.LOCK) {
      if (myChildren == null) {
        final AntProject project = getAntProject();
        final ArrayList<PsiElement> children = new ArrayList<PsiElement>(3);
        if (myPrologElement != null) {
          children.add(myPrologElement);
        }
        children.add(project);
        if (myEpilogueElement != null) {
          children.add(myEpilogueElement);
        }
        myChildren = children.toArray(new PsiElement[children.size()]);
      }
      return myChildren;
    }
  }

  public PsiElement getFirstChild() {
    return getChildren()[0];
  }

  public PsiElement getLastChild() {
    final PsiElement[] psiElements = getChildren();
    return psiElements[psiElements.length - 1];
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "AntFile[" + getName() + "]";
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      myChildren = null;
      myPrologElement = null;
      myProject = null;
      myEpilogueElement = null;
      for (String classname : myCustomDefinitions) {
        myTypeDefinitionArray = null;
        myTypeDefinitions.remove(classname);
      }
      myTargetDefinition = null;
    }
  }

  public void subtreeChanged() {
    clearCaches();
  }

  @NotNull
  public XmlFile getSourceElement() {
    return (XmlFile)getViewProvider().getPsi(StdLanguages.XML);
  }

  public AntElement getAntParent() {
    return null;
  }

  public AntFile getAntFile() {
    return this;
  }

  public AntProject getAntProject() {
    // the following line is necessary only to satisfy the "sync/unsync context" inspection
    synchronized (PsiLock.LOCK) {
      if (myProject != null) return myProject;
      final XmlFile baseFile = getSourceElement();
      final XmlDocument document = baseFile.getDocument();
      assert document != null;
      final XmlTag tag = document.getRootTag();
      assert tag != null;
      final String fileText = baseFile.getText();
      final int projectStart = tag.getTextRange().getStartOffset();
      if (projectStart > 0) {
        myPrologElement = new AntOuterProjectElement(this, 0, fileText.substring(0, projectStart));
      }
      final int projectEnd = tag.getTextRange().getEndOffset();
      if (projectEnd < fileText.length()) {
        myEpilogueElement = new AntOuterProjectElement(this, projectEnd, fileText.substring(projectEnd));
      }
      myProject = new AntProjectImpl(this, tag, createProjectDefinition());
      ((AntProjectImpl)myProject).loadPredefinedProperties(myAntProject);
      return myProject;
    }
  }

  public void setProperty(final String name, final PsiElement element) {
  }

  @Nullable
  public PsiElement getProperty(final String name) {
    return null;
  }

  @NotNull
  public PsiElement[] getProperties() {
    return PsiElement.EMPTY_ARRAY;
  }

  public AntElement lightFindElementAt(int offset) {
    synchronized (PsiLock.LOCK) {
      if (myProject == null) return this;
      final TextRange projectRange = myProject.getTextRange();
      if (offset < projectRange.getStartOffset() || offset >= projectRange.getEndOffset()) return this;
      return myProject.lightFindElementAt(offset);
    }
  }

  @NotNull
  public AntTypeDefinition[] getBaseTypeDefinitions() {
    final int defCount = myTypeDefinitions.size();
    if (myTypeDefinitionArray == null || myTypeDefinitionArray.length != defCount) {
      getBaseTypeDefinition(null);
      myTypeDefinitionArray = myTypeDefinitions.values().toArray(new AntTypeDefinition[defCount]);
    }
    return myTypeDefinitionArray;
  }

  @Nullable
  public AntTypeDefinition getBaseTypeDefinition(final String className) {
    if (myTypeDefinitions == null) {
      myTypeDefinitions = new HashMap<String, AntTypeDefinition>();
      myCustomDefinitions = new HashSet<String>();
      myAntProject = new Project();
      myAntProject.init();
      // first, create task definitons
      updateTypeDefinitions(myAntProject.getTaskDefinitions(), true);
      // second, create definitions of data types
      updateTypeDefinitions(myAntProject.getDataTypeDefinitions(), false);
    }
    return myTypeDefinitions.get(className);
  }

  @NotNull
  public AntTypeDefinition getTargetDefinition() {
    getBaseTypeDefinition(null);
    if (myTargetDefinition == null) {
      @NonNls final HashMap<String, AntAttributeType> targetAttrs = new HashMap<String, AntAttributeType>();
      targetAttrs.put("name", AntAttributeType.STRING);
      targetAttrs.put("depends", AntAttributeType.STRING);
      targetAttrs.put("if", AntAttributeType.STRING);
      targetAttrs.put("unless", AntAttributeType.STRING);
      targetAttrs.put("description", AntAttributeType.STRING);
      final HashMap<AntTypeId, String> targetElements = new HashMap<AntTypeId, String>();
      for (AntTypeDefinition def : getBaseTypeDefinitions()) {
        targetElements.put(def.getTypeId(), def.getClassName());
      }
      myTargetDefinition = new AntTypeDefinitionImpl(new AntTypeId("target"), Target.class.getName(), false, targetAttrs, targetElements);
      registerCustomType(myTargetDefinition);
    }
    return myTargetDefinition;
  }

  public void registerCustomType(final AntTypeDefinition def) {
    myTypeDefinitionArray = null;
    final String classname = def.getClassName();
    myTypeDefinitions.put(classname, def);
    myCustomDefinitions.add(classname);
    if (myTargetDefinition != null && myTargetDefinition != def) {
      myTargetDefinition = null;
    }
  }

  public void unregisterCustomType(final AntTypeDefinition def) {
    myTypeDefinitionArray = null;
    final String classname = def.getClassName();
    myTypeDefinitions.remove(classname);
    myCustomDefinitions.remove(classname);
    if (myTargetDefinition != null && myTargetDefinition != def) {
      myTargetDefinition = null;
    }
  }

  private void updateTypeDefinitions(final Hashtable ht, final boolean isTask) {
    if (ht == null) return;
    final Enumeration types = ht.keys();
    while (types.hasMoreElements()) {
      final String typeName = (String)types.nextElement();
      final Class typeClass = (Class)ht.get(typeName);
      AntTypeDefinition def = createTypeDefinition(new AntTypeId(typeName), typeClass, isTask);
      if (def != null) {
        myTypeDefinitions.put(def.getClassName(), def);
        /**
         * some types are defined only as nested elements, project doesn't return their classes
         * these elements can exist only in context of another element and are defined as subclasses of its class
         * so we should manually update our type definitions map with such elements
         */
        final IntrospectionHelper helper = getHelperExceptionSafe(typeClass);
        if (helper != null) {
          final Enumeration nestedEnum = helper.getNestedElements();
          while (nestedEnum.hasMoreElements()) {
            final String nestedElement = (String)nestedEnum.nextElement();
            final Class clazz = (Class)helper.getNestedElementMap().get(nestedElement);
            if (myTypeDefinitions.get(clazz.getName()) == null) {
              AntTypeDefinition nestedDef = createTypeDefinition(new AntTypeId(nestedElement), clazz, false);
              if (nestedDef != null) {
                myTypeDefinitions.put(nestedDef.getClassName(), nestedDef);
              }
            }
          }
        }
      }
    }
  }

  private AntTypeDefinition createProjectDefinition() {
    getBaseTypeDefinition(null);
    @NonNls final HashMap<String, AntAttributeType> projectAttrs = new HashMap<String, AntAttributeType>();
    projectAttrs.put("name", AntAttributeType.STRING);
    projectAttrs.put("default", AntAttributeType.STRING);
    projectAttrs.put("basedir", AntAttributeType.STRING);
    final HashMap<AntTypeId, String> projectElements = new HashMap<AntTypeId, String>();
    for (AntTypeDefinition def : getBaseTypeDefinitions()) {
      projectElements.put(def.getTypeId(), def.getClassName());
    }
    final AntTypeDefinition def = getTargetDefinition();
    projectElements.put(def.getTypeId(), def.getClassName());
    return new AntTypeDefinitionImpl(new AntTypeId("project"), Project.class.getName(), false, projectAttrs, projectElements);
  }

  static AntTypeDefinition createTypeDefinition(final AntTypeId id, final Class typeClass, final boolean isTask) {
    final IntrospectionHelper helper = getHelperExceptionSafe(typeClass);
    if (helper == null) return null;
    final HashMap<String, AntAttributeType> attributes = new HashMap<String, AntAttributeType>();
    final Enumeration attrEnum = helper.getAttributes();
    while (attrEnum.hasMoreElements()) {
      String attr = (String)attrEnum.nextElement();
      final Class attrClass = helper.getAttributeType(attr);
      if (int.class.equals(attrClass)) {
        attributes.put(attr, AntAttributeType.INTEGER);
      }
      else if (boolean.class.equals(attrClass)) {
        attributes.put(attr, AntAttributeType.BOOLEAN);
      }
      else {
        attributes.put(attr.toLowerCase(Locale.US), AntAttributeType.STRING);
      }
    }
    final HashMap<AntTypeId, String> nestedDefinitions = new HashMap<AntTypeId, String>();
    final Enumeration nestedEnum = helper.getNestedElements();
    while (nestedEnum.hasMoreElements()) {
      final String nestedElement = (String)nestedEnum.nextElement();
      final String className = ((Class)helper.getNestedElementMap().get(nestedElement)).getName();
      nestedDefinitions.put(new AntTypeId(nestedElement), className);
    }
    return new AntTypeDefinitionImpl(id, typeClass.getName(), isTask, attributes, nestedDefinitions);
  }

  private static IntrospectionHelper getHelperExceptionSafe(Class c) {
    try {
      return IntrospectionHelper.getHelper(c);
    }
    catch (Throwable e) {
      // TODO[lvo]: please review.
      // Problem creating introspector like classes this task depends on cannot be loaded or missing.
    }
    return null;
  }
}
