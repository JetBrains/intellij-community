package com.intellij.lang.ant.psi.impl;

import com.intellij.extapi.psi.LightPsiFileBase;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.impl.AntBuildFileImpl;
import com.intellij.lang.ant.config.impl.AntClassLoader;
import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntProperty;
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

import java.lang.reflect.Method;
import java.util.*;

@SuppressWarnings({"UseOfObsoleteCollectionType"})
public class AntFileImpl extends LightPsiFileBase implements AntFile {
  private AntProject myProject;
  private AntElement myPrologElement;
  private AntElement myEpilogueElement;
  private PsiElement[] myChildren;
  private AntClassLoader myClassLoader;
  private Hashtable myProjectProperties;
  /**
   * Map of propeties set outside.
   */
  private Map<String, String> myExternalProperties;
  /**
   * Map of class names to task definitions.
   */
  private Map<String, AntTypeDefinition> myTypeDefinitions;
  private AntTypeDefinition[] myTypeDefinitionArray;
  private AntTypeDefinition myTargetDefinition;
  /**
   * Map of nested elements specially for the project element.
   * It's updated together with the set of custom definitons.
   */
  private HashMap<AntTypeId, String> myProjectElements;

  @NonNls private static final String INIT_METHOD_NAME = "init";
  @NonNls private static final String GET_TASK_DEFINITIONS_METHOD_NAME = "getTaskDefinitions";
  @NonNls private static final String GET_DATA_TYPE_DEFINITIONS_METHOD_NAME = "getDataTypeDefinitions";
  @NonNls private static final String GET_PROPERTIES_METHOD_NAME = "getProperties";

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

  public AntElementRole getRole() {
    return AntElementRole.NULL_ROLE;
  }

  public void setProperty(@NotNull final String name, @NotNull final String value) {
    synchronized (PsiLock.LOCK) {
      if (myExternalProperties == null) {
        myExternalProperties = new HashMap<String, String>();
      }
      myExternalProperties.put(name, value);
    }
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

  public AntFileImpl copyLight(final FileViewProvider viewProvider) {
    return new AntFileImpl(viewProvider);
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

  @NotNull
  public AntClassLoader getClassLoader() {
    if (myClassLoader == null) {
      final AntBuildFileImpl buildFile = (AntBuildFileImpl)getSourceElement().getCopyableUserData(XmlFile.ANT_BUILD_FILE);
      if (buildFile != null) {
        myClassLoader = buildFile.getClassLoader();
      }
      else {
        final AntConfigurationBase configuration = AntConfigurationBase.getInstance(getProject());
        final AntInstallation antInstallation = configuration != null ? configuration.getProjectDefaultAnt() : null;
        if (antInstallation != null) {
          myClassLoader = antInstallation.getClassLoader();
        }
        else {
          myClassLoader = new AntClassLoader();
        }
      }
    }
    return myClassLoader;
  }

  @Nullable
  public AntElement getAntParent() {
    return null;
  }

  public AntFile getAntFile() {
    return this;
  }

  public AntProject getAntProject() {
    // the following line is necessary only to satisfy the "sync/unsync context" inspection
    synchronized (PsiLock.LOCK) {
      if (myProject == null) {
        final XmlFile baseFile = getSourceElement();
        final XmlDocument document = baseFile.getDocument();
        assert document != null;
        final XmlTag tag = document.getRootTag();
        if (tag == null) return null;
        final String fileText = baseFile.getText();
        final int projectStart = tag.getTextRange().getStartOffset();
        if (projectStart > 0) {
          myPrologElement = new AntOuterProjectElement(this, 0, fileText.substring(0, projectStart));
        }
        final int projectEnd = tag.getTextRange().getEndOffset();
        if (projectEnd < fileText.length()) {
          myEpilogueElement = new AntOuterProjectElement(this, projectEnd, fileText.substring(projectEnd));
        }
        final AntProjectImpl project = new AntProjectImpl(this, tag, createProjectDefinition());
        project.loadPredefinedProperties(myProjectProperties, myExternalProperties);
        myProject = project;
      }
      return myProject;
    }
  }

  @Nullable
  public AntProperty getProperty(final String name) {
    return null;
  }

  public void setProperty(final String name, final AntProperty element) {
    throw new RuntimeException("Invoke AntProject.setProperty() instead.");
  }

  @NotNull
  public AntProperty[] getProperties() {
    return AntProperty.EMPTY_ARRAY;
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
    synchronized (PsiLock.LOCK) {
      final int defCount = myTypeDefinitions.size();
      if (myTypeDefinitionArray == null || myTypeDefinitionArray.length != defCount) {
        getBaseTypeDefinition(null);
        myTypeDefinitionArray = myTypeDefinitions.values().toArray(new AntTypeDefinition[defCount]);
      }
      return myTypeDefinitionArray;
    }
  }

  @Nullable
  public AntTypeDefinition getBaseTypeDefinition(final String className) {
    synchronized (PsiLock.LOCK) {
      if (myTypeDefinitions == null) {
        myTypeDefinitions = new HashMap<String, AntTypeDefinition>();
        myProjectElements = new HashMap<AntTypeId, String>();
        final ReflectedProject reflectedProject = new ReflectedProject(getClassLoader());
        if (reflectedProject.myProject != null) {
          // first, create task definitons
          updateTypeDefinitions(reflectedProject.myTaskDefinitions, true);
          // second, create definitions of data types
          updateTypeDefinitions(reflectedProject.myDataTypeDefinitions, false);
          myProjectProperties = reflectedProject.myProperties;
        }
      }
      return myTypeDefinitions.get(className);
    }
  }

  @NotNull
  public AntTypeDefinition getTargetDefinition() {
    getBaseTypeDefinition(null);
    synchronized (PsiLock.LOCK) {
      if (myTargetDefinition == null) {
        @NonNls final HashMap<String, AntAttributeType> targetAttrs = new HashMap<String, AntAttributeType>();
        targetAttrs.put("name", AntAttributeType.STRING);
        targetAttrs.put("depends", AntAttributeType.STRING);
        targetAttrs.put("if", AntAttributeType.STRING);
        targetAttrs.put("unless", AntAttributeType.STRING);
        targetAttrs.put("description", AntAttributeType.STRING);
        final HashMap<AntTypeId, String> targetElements = new HashMap<AntTypeId, String>();
        for (AntTypeDefinition def : getBaseTypeDefinitions()) {
          if (def.isTask() || targetElements.get(def.getTypeId()) == null) {
            targetElements.put(def.getTypeId(), def.getClassName());
          }
        }
        myTargetDefinition = new AntTypeDefinitionImpl(new AntTypeId("target"), Target.class.getName(), false, targetAttrs, targetElements);
        registerCustomType(myTargetDefinition);
      }
      return myTargetDefinition;
    }
  }

  public void registerCustomType(final AntTypeDefinition def) {
    synchronized (PsiLock.LOCK) {
      myTypeDefinitionArray = null;
      final String classname = def.getClassName();
      myTypeDefinitions.put(classname, def);
      myProjectElements.put(def.getTypeId(), classname);
      if (myTargetDefinition != null && myTargetDefinition != def) {
        myTargetDefinition = null;
      }
    }
  }

  public void unregisterCustomType(final AntTypeDefinition def) {
    synchronized (PsiLock.LOCK) {
      myTypeDefinitionArray = null;
      final String classname = def.getClassName();
      myTypeDefinitions.remove(classname);
      myProjectElements.remove(def.getTypeId());
      if (myTargetDefinition != null && myTargetDefinition != def) {
        myTargetDefinition = null;
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void updateTypeDefinitions(final Hashtable ht, final boolean isTask) {
    if (ht == null) return;
    final Enumeration types = ht.keys();
    while (types.hasMoreElements()) {
      String typeName = (String)types.nextElement();
      /**
       * Hardcode for <javadoc> task (IDEADEV-6731).
       */
      if (isTask && typeName.equals("javadoc2")) {
        typeName = "javadoc";
      }
      final Class typeClass = (Class)ht.get(typeName);
      final AntTypeId typeId = new AntTypeId(typeName);
      final AntTypeDefinition def = createTypeDefinition(typeId, typeClass, isTask);
      if (def != null) {
        final String className = def.getClassName();
        myTypeDefinitions.put(className, def);
        myProjectElements.put(typeId, className);
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
              final AntTypeDefinition nestedDef = createTypeDefinition(new AntTypeId(nestedElement), clazz, false);
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
    final AntTypeDefinition def = getTargetDefinition();
    myProjectElements.put(def.getTypeId(), def.getClassName());
    return new AntTypeDefinitionImpl(new AntTypeId("project"), Project.class.getName(), false, projectAttrs, myProjectElements);
  }

  @Nullable
  static AntTypeDefinition createTypeDefinition(final AntTypeId id, final Class typeClass, final boolean isTask) {
    final IntrospectionHelper helper = getHelperExceptionSafe(typeClass);
    if (helper == null) return null;
    final HashMap<String, AntAttributeType> attributes = new HashMap<String, AntAttributeType>();
    final Enumeration attrEnum = helper.getAttributes();
    while (attrEnum.hasMoreElements()) {
      final String attr = (String)attrEnum.nextElement();
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

  @Nullable
  private static IntrospectionHelper getHelperExceptionSafe(Class c) {
    try {
      return IntrospectionHelper.getHelper(c);
    }
    catch (Throwable e) {
      // can't be
    }
    return null;
  }

  private static final class ReflectedProject {
    private Object myProject;
    private Hashtable myTaskDefinitions;
    private Hashtable myDataTypeDefinitions;
    private Hashtable myProperties;

    public ReflectedProject(final AntClassLoader classLoader) {
      try {
        final Class projectClass = classLoader.loadClass("org.apache.tools.ant.Project");
        myProject = projectClass.newInstance();
        Method method = projectClass.getMethod(INIT_METHOD_NAME);
        method.invoke(myProject);
        method = getMethod(projectClass, GET_TASK_DEFINITIONS_METHOD_NAME);
        myTaskDefinitions = (Hashtable)method.invoke(myProject);
        method = getMethod(projectClass, GET_DATA_TYPE_DEFINITIONS_METHOD_NAME);
        myDataTypeDefinitions = (Hashtable)method.invoke(myProject);
        method = getMethod(projectClass, GET_PROPERTIES_METHOD_NAME);
        myProperties = (Hashtable)method.invoke(myProject);
      }
      catch (Exception e) {
        myProject = null;
      }
    }

    private static Method getMethod(final Class introspectionHelperClass, final String name) throws NoSuchMethodException {
      final Method method;
      method = introspectionHelperClass.getMethod(name);
      if (!method.isAccessible()) {
        method.setAccessible(true);
      }
      return method;
    }
  }
}

