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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

@SuppressWarnings({"UseOfObsoleteCollectionType"})
public class AntFileImpl extends LightPsiFileBase implements AntFile {
  private AntProject myProject;
  private AntElement myPrologElement;
  private PsiElement[] myChildren;
  private Project myAntProject;
  /**
   * Map of class names to task definitions.
   */
  private Map<String, AntTypeDefinition> myTypeDefinitions;
  private AntTypeDefinition[] myTypeDefinitionArrays;
  private AntTypeDefinition myTargetDefinition;


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
    if (myChildren == null) {
      final AntProject project = getAntProject();
      myChildren = (myPrologElement == null) ? new PsiElement[]{project} : new PsiElement[]{myPrologElement, project};
    }
    return myChildren;
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
    myChildren = null;
    myProject = null;
    myPrologElement = null;
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
    if (myProject != null) return myProject;
    final XmlFile baseFile = getSourceElement();
    final XmlDocument document = baseFile.getDocument();
    assert document != null;
    final XmlTag tag = document.getRootTag();
    assert tag != null;
    final int projectStart = tag.getTextRange().getStartOffset();
    if (projectStart > 0) {
      myPrologElement = new AntOuterProjectElement(this, 0, baseFile.getText().substring(0, projectStart));
    }
    myProject = new AntProjectImpl(this, tag, createProjectDefinition());
    ((AntProjectImpl)myProject).loadPredefinedProperties(myAntProject);
    return myProject;
  }

  @Nullable
  public PsiFile findFileByName(final String name) {
    return null;
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

  @NotNull
  public AntTypeDefinition[] getBaseTypeDefinitions() {
    if (myTypeDefinitionArrays != null) return myTypeDefinitionArrays;
    getBaseTypeDefinition(null);
    return myTypeDefinitionArrays = myTypeDefinitions.values().toArray(new AntTypeDefinition[myTypeDefinitions.size()]);
  }

  @Nullable
  public AntTypeDefinition getBaseTypeDefinition(final String className) {
    if (myTypeDefinitions != null) return myTypeDefinitions.get(className);
    myTypeDefinitions = new HashMap<String, AntTypeDefinition>();
    myAntProject = new Project();
    myAntProject.init();
    // first, create task definitons without nested elements
    updateTypeDefinitions(myAntProject.getTaskDefinitions(), true);
    // second, create definitions for data types
    updateTypeDefinitions(myAntProject.getDataTypeDefinitions(), false);
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

  public void registerCustomType(final AntTypeDefinition definition) {
    myTypeDefinitionArrays = null;
    myTypeDefinitions.put(definition.getClassName(), definition);
  }

  private void updateTypeDefinitions(final Hashtable ht, final boolean isTask) {
    if (ht == null) return;
    final Enumeration types = ht.keys();
    while (types.hasMoreElements()) {
      final String typeName = (String)types.nextElement();
      final Class typeClass = (Class)ht.get(typeName);
      AntTypeDefinition def = createTypeDefinition(new AntTypeId(typeName, null), typeClass, isTask);
      if (def != null) {
        myTypeDefinitions.put(def.getClassName(), def);
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
      if (def.isTask()) {
        projectElements.put(def.getTypeId(), def.getClassName());
      }
    }
    final AntTypeDefinition def = getTargetDefinition();
    projectElements.put(def.getTypeId(), def.getClassName());
    return new AntTypeDefinitionImpl(new AntTypeId("project"), Project.class.getName(), false, projectAttrs, projectElements);
  }

  static AntTypeDefinition createTypeDefinition(final AntTypeId id, final Class taskClass, final boolean isTask) {
    final IntrospectionHelper helper = getHelperExceptionSafe(taskClass);
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
        attributes.put(attr, AntAttributeType.STRING);
      }
    }
    final HashMap<AntTypeId, String> nestedDefinitions = new HashMap<AntTypeId, String>();
    final Enumeration nestedEnum = helper.getNestedElements();
    while (nestedEnum.hasMoreElements()) {
      final String nestedElement = (String)nestedEnum.nextElement();
      final String className = ((Class)helper.getNestedElementMap().get(nestedElement)).getName();
      nestedDefinitions.put(new AntTypeId(nestedElement), className);
    }
    return new AntTypeDefinitionImpl(id, taskClass.getName(), isTask, attributes, nestedDefinitions);
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
