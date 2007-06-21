package com.intellij.lang.ant.psi.impl;

import com.intellij.extapi.psi.LightPsiFileBase;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.impl.AntBuildFileImpl;
import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import com.intellij.lang.ant.misc.AntStringInterner;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.changes.AntChangeVisitor;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Alarm;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.StringBuilderSpinAllocator;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Property;
import org.apache.tools.ant.types.DataType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.util.*;

@SuppressWarnings({"UseOfObsoleteCollectionType"})
public class AntFileImpl extends LightPsiFileBase implements AntFile {
  
  @NonNls public static final String PROJECT_TAG = "project";
  @NonNls public static final String TARGET_TAG = "target";
  @NonNls public static final String IMPORT_TAG = "import";
  @NonNls public static final String FORMAT_TAG = "format";
  @NonNls public static final String JAVADOC2_TAG = "javadoc2";
  @NonNls public static final String JAVADOC_TAG = "javadoc";
  @NonNls public static final String UNWAR_TAG = "unwar";
  @NonNls public static final String UNJAR_TAG = "unjar";
  @NonNls public static final String PROPERTY = "property";

  @NonNls public static final String UNZIP_TAG = "unzip";
  @NonNls public static final String NAME_ATTR = "name";
  @NonNls public static final String ID_ATTR = "id";
  @NonNls public static final String DEPENDS_ATTR = "depends";
  @NonNls public static final String IF_ATTR = "if";
  @NonNls public static final String UNLESS_ATTR = "unless";
  @NonNls public static final String DESCRIPTION_ATTR = "description";
  @NonNls public static final String DEFAULT_ATTR = "default";
  @NonNls public static final String BASEDIR_ATTR = "basedir";
  @NonNls public static final String FILE_ATTR = "file";
  @NonNls public static final String PREFIX_ATTR = "prefix";

  @NonNls public static final String DEFAULT_ENVIRONMENT_PREFIX = "env.";
  
  private AntProject myProject;
  private AntElement myPrologElement;
  private AntElement myEpilogueElement;
  private PsiElement[] myChildren;

  private ClassLoader myClassLoader;
  private Hashtable myProjectProperties;
  private boolean myNeedPropertiesRebuild = false;
  private Map<String, AntProperty> myProperties;
  private volatile AntProperty[] myPropertiesArray;
  private volatile PropertiesWatcher myDependentFilesWatcher;
  private List<String> myEnvPrefixes;


  /**
   * Map of propeties set outside.
   */
  private volatile Map<String, String> myExternalProperties;
  /**
   * Map of class names to task definitions.
   */
  private volatile Map<String, AntTypeDefinition> myTypeDefinitions;
  private volatile AntTypeDefinition[] myTypeDefinitionArray;
  private volatile AntTypeDefinition myTargetDefinition;
  /**
   * Map of nested elements specially for the project element.
   * It's updated together with the set of custom definitons.
   */
  private volatile HashMap<AntTypeId, String> myProjectElements;
  private volatile long myModificationCount = 0;

  public AntFileImpl(final FileViewProvider viewProvider) {
    super(viewProvider, AntSupport.getLanguage());
    AntConfiguration.initAntSupport(getProject());
  }

  public void acceptAntElementVisitor(@NotNull final AntElementVisitor visitor) {
    visitor.visitAntFile(this);
  }

  @NotNull
  public FileType getFileType() {
    return getViewProvider().getVirtualFile().getFileType();
  }

  public PsiElement getOriginalElement() {
    return getSourceElement().getOriginalElement();
  }

  public VirtualFile getVirtualFile() {
    VirtualFile result = getSourceElement().getVirtualFile();
    if (result == null) {
      final PsiFile origFile = getSourceElement().getOriginalFile();
      if (origFile != null) {
        result = origFile.getVirtualFile();
      }
    }
    return result;
  }

  public AntElementRole getRole() {
    return AntElementRole.NULL_ROLE;
  }

  public boolean canRename() {
    return isPhysical();
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

  @Nullable
  public VirtualFile getContainingPath() {
    VirtualFile result = getVirtualFile();
    return (result == null) ? null : result.getParent();
  }

  public void clearCachesWithTypeDefinitions() {
    synchronized (PsiLock.LOCK) {
      myTypeDefinitions = null;
      myTypeDefinitionArray = null;
      myTargetDefinition = null;
      myProjectElements = null;
      clearCaches();
    }
  }
  
  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      if (myChildren != null) {
        myChildren = null;
        myPrologElement = null;
        myProject = null;
        myEpilogueElement = null;
        myTargetDefinition = null;
        myClassLoader = null;
        myEnvPrefixes = null;
        incModificationCount();
      }
    }
  }

  public void invalidateProperties() {
    synchronized (PsiLock.LOCK) {
      myProperties = null;
      myPropertiesArray = null;
      myNeedPropertiesRebuild = true;
    }
  }

  public synchronized void incModificationCount() {
    ++myModificationCount;
    for (final AntFile file : AntSupport.getImpotingFiles(this)) {
      file.clearCaches();
    }
  }

  public synchronized long getModificationCount() {
    return myModificationCount;
  }

  public void subtreeChanged() {
    clearCaches();
  }

  @NotNull
  public XmlFile getSourceElement() {
    return (XmlFile)getViewProvider().getPsi(StdLanguages.XML);
  }

  @NotNull
  public ClassLoader getClassLoader() {
    if (myClassLoader == null) {
      final AntBuildFileImpl buildFile = (AntBuildFileImpl)getSourceElement().getCopyableUserData(XmlFile.ANT_BUILD_FILE);
      if (buildFile != null) {
        myClassLoader = buildFile.getClassLoader();
      }
      else {
        final AntConfigurationBase configuration = AntConfigurationBase.getInstance(getProject());
        AntInstallation antInstallation = null;
        if (configuration != null) {
          antInstallation = configuration.getProjectDefaultAnt();
        }
        if (antInstallation == null) {
          antInstallation = GlobalAntConfiguration.getInstance().getBundledAnt();
        }
        assert antInstallation != null;
        myClassLoader = antInstallation.getClassLoader();
      }
    }
    return myClassLoader;
  }

  @NotNull
  public AntInstallation getAntInstallation() {
    final AntBuildFileImpl buildFile = (AntBuildFileImpl)getSourceElement().getCopyableUserData(XmlFile.ANT_BUILD_FILE);
    if (buildFile != null) {
      final AntInstallation assignedInstallation = AntBuildFileImpl.ANT_INSTALLATION.get(buildFile.getAllOptions());
      if (assignedInstallation != null) {
        return assignedInstallation;
      }
    }
    
    final AntConfigurationBase configuration = AntConfigurationBase.getInstance(getProject());
    if (configuration != null) {
      final AntInstallation projectDefaultInstallation = configuration.getProjectDefaultAnt();
      if (projectDefaultInstallation != null) {
        return projectDefaultInstallation;
      }
    }
    
    return GlobalAntConfiguration.getInstance().getBundledAnt();
  }

  @Nullable
  public AntElement getAntParent() {
    return null;
  }

  public AntFile getAntFile() {
    return this;
  }

  @Nullable
  public AntProject getAntProject() {
    boolean updateBuilFile = false;
    try {
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
          myProject = project;
          buildPropertiesMap();
          for (final AntFile imported : project.getImportedFiles()) {
            if (imported.isPhysical()) {
              AntSupport.registerDependency(this, imported);
            }
          }
          updateBuilFile = true;
        }
        return myProject;
      }
    }
    finally {
      if (updateBuilFile) {
        AntChangeVisitor.updateBuildFile(this);
      }
    }
  }              

  @Nullable
  public AntProperty getProperty(final String name) {
    if (name == null) {
      return null;
    }
    synchronized (PsiLock.LOCK) {
      return myProperties != null? myProperties.get(name) : null;
    }
  }

  public void buildPropertiesIfNeeded() {
    if (myNeedPropertiesRebuild || (myDependentFilesWatcher != null && myDependentFilesWatcher.needRebuildProperties())) {
      buildPropertiesMap();
    }
  }

  private void buildPropertiesMap() {
    myNeedPropertiesRebuild = false;
    myDependentFilesWatcher = null;
    myProperties = new HashMap<String, AntProperty>();
    myPropertiesArray = null;
    loadPredefinedProperties(myProjectProperties, myExternalProperties);
    final List<PsiFile> dependentFiles = PropertiesBuilder.defineProperties(this);
    myDependentFilesWatcher = new PropertiesWatcher(dependentFiles);
  }

  @SuppressWarnings({"UseOfObsoleteCollectionType"})
  void loadPredefinedProperties(final Hashtable properties, final Map<String, String> externalProps) {
    final Enumeration props = (properties != null) ? properties.keys() : (new Hashtable()).keys();
    final @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    builder.append("<project name=\"predefined properties\">");
    try {
      while (props.hasMoreElements()) {
        final String name = (String)props.nextElement();
        final String value = (String)properties.get(name);
        builder.append("<property name=\"");
        builder.append(name);
        builder.append("\" value=\"");
        builder.append(value);
        builder.append("\"/>");
      }
      final Map<String, String> envMap = System.getenv();
      for (final String name : envMap.keySet()) {
        final String value = envMap.get(name);
        if (name.length() > 0) {
          builder.append("<property name=\"");
          builder.append(DEFAULT_ENVIRONMENT_PREFIX);
          builder.append(name);
          builder.append("\" value=\"");
          builder.append(value);
          builder.append("\"/>");
        }
      }
      if (externalProps != null) {
        for (final String name : externalProps.keySet()) {
          final String value = externalProps.get(name);
          builder.append("<property name=\"");
          builder.append(name);
          builder.append("\" value=\"");
          builder.append(value);
          builder.append("\"/>");
        }
      }
      final VirtualFile file = getVirtualFile();
      final AntProject antProject = getAntProject();
      String basedir = antProject.getBaseDir();
      if (basedir == null) {
        basedir = ".";
      }
      if (file != null && !new File(basedir).isAbsolute()) {
        final VirtualFile dir = file.getParent();
        if (dir != null) {
          try {
            basedir = new File(dir.getPath(), basedir).getCanonicalPath();
          }
          catch (IOException e) {
            // ignore
          }
        }
      }
      if (basedir != null) {
        builder.append("<property name=\"basedir\" value=\"");
        builder.append(basedir);
        builder.append("\"/>");
      }
      builder.append("<property name=\"ant.home\" value=\"\"/>");
      builder.append("<property name=\"ant.version\" value=\"1.6.5\"/>");
      builder.append("<property name=\"ant.project.name\" value=\"");
      final String name = antProject.getName();
      builder.append((name == null) ? "" : name);
      builder.append("\"/>");
      builder.append("<property name=\"ant.java.version\" value=\"");
      builder.append(SystemInfo.JAVA_VERSION);
      builder.append("\"/>");
      if (file != null) {
        final String path = file.getPath();
        builder.append("<property name=\"ant.file\" value=\"");
        builder.append(path);
        builder.append("\"/>");
        if (name != null) {
          builder.append("<property name=\"ant.file.");
          builder.append(name);
          builder.append("\" value=\"${ant.file}\"/>");
        }
      }
      builder.append("</project>");
      final XmlFile xmlFile = (XmlFile)getManager().getElementFactory()
        .createFileFromText("dummy.xml", StdFileTypes.XML, builder, LocalTimeCounter.currentTime(), false, false);
      final XmlDocument document = xmlFile.getDocument();
      if (document == null) return;
      final XmlTag rootTag = document.getRootTag();
      if (rootTag == null) return;
      final AntTypeDefinition propertyDef = getAntFile().getBaseTypeDefinition(Property.class.getName());
      AntProject fakeProject = new AntProjectImpl(this, rootTag, antProject.getTypeDefinition());
      for (final XmlTag tag : rootTag.getSubTags()) {
        final AntPropertyImpl property = new AntPropertyImpl(fakeProject, tag, propertyDef) {
          public PsiFile getContainingFile() {
            return getSourceElement().getContainingFile();
          }

          public PsiElement getNavigationElement() {
            if (AntFileImpl.BASEDIR_ATTR.equals(getName())) {
              final XmlAttribute attr = antProject.getSourceElement().getAttribute(AntFileImpl.BASEDIR_ATTR, null);
              if (attr != null) {
                return attr;
              }
            }
            return super.getNavigationElement();
          }
        };
        myProperties.put(property.getName(), property);
      }
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }


  public void setProperty(final String name, final AntProperty element) {
    synchronized (PsiLock.LOCK) {
      if (myNeedPropertiesRebuild) {
        buildPropertiesMap();
      }
      if (myProperties == null) {
        myProperties = new HashMap<String, AntProperty>();
        myPropertiesArray = null;
      }
      if (!myProperties.containsKey(name)) {
        myProperties.put(name, element);
        myPropertiesArray = null;
      }
    }
  }

  @NotNull
  public AntProperty[] getProperties() {
    synchronized (PsiLock.LOCK) {
      if (myNeedPropertiesRebuild) {
        buildPropertiesMap();
      }
      if (myProperties == null) {
        return AntProperty.EMPTY_ARRAY;
      }
      if (myPropertiesArray == null) {
        myPropertiesArray = myProperties.values().toArray(new AntProperty[myProperties.size()]);
      }
      return myPropertiesArray;
    }
  }

  public void addEnvironmentPropertyPrefix(@NotNull final String envPrefix) {
    synchronized (PsiLock.LOCK) {
      checkEnvList();
      final String env = (envPrefix.endsWith(".")) ? envPrefix : envPrefix + '.';
      if (myEnvPrefixes.indexOf(env) < 0) {
        myEnvPrefixes.add(env);
        for (final AntProperty element : getProperties()) {
          final String name = element.getName();
          if (name != null && name.startsWith(DEFAULT_ENVIRONMENT_PREFIX)) {
            setProperty(env + name.substring(DEFAULT_ENVIRONMENT_PREFIX.length()), element);
          }
        }
      }
    }
  }

  public boolean isEnvironmentProperty(@NotNull final String propName) {
    synchronized (PsiLock.LOCK) {
      checkEnvList();
      if (!propName.endsWith(".")) {
        for (final String prefix : myEnvPrefixes) {
          if (propName.startsWith(prefix)) {
            return true;
          }
        }
      }
      return false;
    }
  }

  public List<String> getEnvironmentPrefixes() {
    synchronized (PsiLock.LOCK) {
      checkEnvList();
      return myEnvPrefixes;
    }
  }


  private void checkEnvList() {
    if (myEnvPrefixes == null) {
      myEnvPrefixes = new ArrayList<String>();
      myEnvPrefixes.add(DEFAULT_ENVIRONMENT_PREFIX);
    }
  }

  public AntElement lightFindElementAt(int offset) {
    synchronized (PsiLock.LOCK) {
      if (myProject == null) return this;
      final TextRange projectRange = myProject.getTextRange();
      if (offset < projectRange.getStartOffset() || offset >= projectRange.getEndOffset()) return this;
      final AntElement prolog = myPrologElement;
      return myProject.lightFindElementAt(offset - ((prolog == null) ? 0 : prolog.getTextLength()));
    }
  }

  @NotNull
  public AntTypeDefinition[] getBaseTypeDefinitions() {
    synchronized (PsiLock.LOCK) {
      if (myTypeDefinitionArray == null || myTypeDefinitions == null || myTypeDefinitionArray.length != myTypeDefinitions.size()) {
        buildTypeDefinitions();
        myTypeDefinitionArray = myTypeDefinitions.values().toArray(new AntTypeDefinition[myTypeDefinitions.size()]);
      }
      return myTypeDefinitionArray;
    }
  }

  @Nullable
  public AntTypeDefinition getBaseTypeDefinition(final String className) {
    synchronized (PsiLock.LOCK) {
      buildTypeDefinitions();
      return myTypeDefinitions.get(className);
    }
  }

  private void buildTypeDefinitions() {
    if (myTypeDefinitions == null) {
      myTypeDefinitions = new HashMap<String, AntTypeDefinition>();
      myProjectElements = new HashMap<AntTypeId, String>();
      final ReflectedProject reflectedProject = ReflectedProject.getProject(getClassLoader());
      if (reflectedProject.myProject != null) {
        final AntInstrospector projectHelper = getHelperExceptionSafe(reflectedProject.myProject.getClass());
        try {
          // first, create task definitons
          updateTypeDefinitions(reflectedProject.myTaskDefinitions, true);
          // second, create definitions of data types
          updateTypeDefinitions(reflectedProject.myDataTypeDefinitions, false);
          myProjectProperties = reflectedProject.myProperties;
        }
        finally {
          if (projectHelper != null) {
            projectHelper.clearCache();
          }
        }
      }
    }
  }

  @Nullable /*will return null in case ant installation is not properly configured*/
  public AntTypeDefinition getTargetDefinition() {
    synchronized (PsiLock.LOCK) {
      buildTypeDefinitions();
      if (myTargetDefinition == null) {
        final Class targetClass = ReflectedProject.getProject(getClassLoader()).myTargetClass;
        final AntTypeDefinition targetDef = createTypeDefinition(new AntTypeId(TARGET_TAG), targetClass, false);
        if (targetDef != null) {
          for (final AntTypeDefinition def : getBaseTypeDefinitions()) {
            final AntTypeId id = def.getTypeId();
            if (canBeUsedInTarget(def)) { 
              targetDef.registerNestedType(id, def.getClassName());
            }
          }
          myTargetDefinition = targetDef;
          registerCustomType(targetDef);
        }
      }
      return myTargetDefinition;
    }
  }

  private boolean canBeUsedInTarget(final AntTypeDefinition def) {
    // if type definition is a task _and_ is visible at the project level
    // custom tasks can define nested types with the same typeId (e.g. "property") but different implementation class
    // such nested types can be used only inside tasks which defined them, but not at a project level
    return (def.isTask() || isDataDype(def)) && isProjectNestedElement(def);
  }

  private boolean isDataDype(final AntTypeDefinition def) {
    try {
      final ClassLoader loader = getClassLoader();
      final Class defClass = loader.loadClass(def.getClassName());
      final Class dataTypeClass = loader.loadClass(DataType.class.getName());
      return dataTypeClass.isAssignableFrom(defClass);
    }
    catch (ClassNotFoundException e) {
      return false;
    }
  }

  private boolean isProjectNestedElement(final AntTypeDefinition def) {
    final AntTypeId id = def.getTypeId();
    if (myProject != null) {
      final AntTypeDefinition projectDef = myProject.getTypeDefinition();
      if (projectDef != null) {
        return def.getClassName().equals(projectDef.getNestedClassName(id));
      }
    }
    return def.getClassName().equals(myProjectElements.get(id));
  }

  public void registerCustomType(final AntTypeDefinition def) {
    synchronized (PsiLock.LOCK) {
      buildTypeDefinitions();
      myTypeDefinitionArray = null;
      final String classname = def.getClassName();
      myTypeDefinitions.put(classname, def);
      if (myTargetDefinition != null && myTargetDefinition != def) {
        if (canBeUsedInTarget(def)) {
          myTargetDefinition.registerNestedType(def.getTypeId(), def.getClassName());
        }
      }
    }
  }

  public void unregisterCustomType(final AntTypeDefinition def) {
    synchronized (PsiLock.LOCK) {
      myTypeDefinitionArray = null;
      final String classname = def.getClassName();
      if (myTypeDefinitions != null) {
        myTypeDefinitions.remove(classname);
      }
      if (myProjectElements != null) {
        final String registeredClassName = myProjectElements.get(def.getTypeId());
        if (registeredClassName != null && registeredClassName.equals(classname)) {
          myProjectElements.remove(def.getTypeId());
        }
      }
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
      if (isTask && typeName.equals(JAVADOC2_TAG)) {
        typeName = JAVADOC_TAG;
      }
      /**
       * Hardcode for <unwar> and <unjar> tasks (IDEADEV-6830).
       */
      if (isTask && (typeName.equals(UNWAR_TAG) || typeName.equals(UNJAR_TAG))) {
        typeName = UNZIP_TAG;
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
        registerNestedDefinitionsRecursively(typeClass);
      }
    }
  }

  private void registerNestedDefinitionsRecursively(final Class parentClass) {
    final AntInstrospector helper = getHelperExceptionSafe(parentClass);
    if (helper != null) {
      final Enumeration nestedEnum = helper.getNestedElements();
      while (nestedEnum.hasMoreElements()) {
        final String nestedElement = (String)nestedEnum.nextElement();
        final Class nestedElementClass = helper.getElementType(nestedElement);
        if (nestedElementClass != null && myTypeDefinitions.get(nestedElementClass.getName()) == null) {
          final AntTypeDefinition nestedDef = createTypeDefinition(new AntTypeId(nestedElement), nestedElementClass, false);
          if (nestedDef != null) {
            myTypeDefinitions.put(nestedDef.getClassName(), nestedDef);
            registerNestedDefinitionsRecursively(nestedElementClass);
          }
        }
      }
    }
  }

  private AntTypeDefinition createProjectDefinition() {
    buildTypeDefinitions();
    @NonNls final HashMap<String, AntAttributeType> projectAttrs = new HashMap<String, AntAttributeType>();
    projectAttrs.put(NAME_ATTR, AntAttributeType.STRING);
    projectAttrs.put(DEFAULT_ATTR, AntAttributeType.STRING);
    projectAttrs.put(BASEDIR_ATTR, AntAttributeType.STRING);
    final AntTypeDefinition def = getTargetDefinition();
    if (def != null) {
      myProjectElements.put(def.getTypeId(), def.getClassName());
    }
    return new AntTypeDefinitionImpl(new AntTypeId(PROJECT_TAG), Project.class.getName(), false, projectAttrs, myProjectElements);
  }

  @Nullable
  static AntTypeDefinition createTypeDefinition(final AntTypeId id, final Class typeClass, final boolean isTask) {
    final AntInstrospector helper = getHelperExceptionSafe(typeClass);
    if (helper == null) {
      return null;
    }
    final HashMap<String, AntAttributeType> attributes = new HashMap<String, AntAttributeType>();
    final Enumeration attrEnum = helper.getAttributes();
    while (attrEnum.hasMoreElements()) {
      final String attr = AntStringInterner.intern(((String)attrEnum.nextElement()).toLowerCase(Locale.US));
      attributes.put(attr, AntAttributeType.create(helper.getAttributeType(attr)));
    }
    final HashMap<AntTypeId, String> nestedDefinitions = new HashMap<AntTypeId, String>();
    final Enumeration nestedEnum = helper.getNestedElements();
    while (nestedEnum.hasMoreElements()) {
      final String nestedElement = (String)nestedEnum.nextElement();
      final Class elementType = helper.getElementType(nestedElement);
      if (elementType != null) {
        final String className = AntStringInterner.intern((elementType.getName()));
        nestedDefinitions.put(new AntTypeId(nestedElement), className);
      }
    }
    return new AntTypeDefinitionImpl(id, typeClass.getName(), isTask, attributes, nestedDefinitions);
  }
  
  @Nullable
  private static AntInstrospector getHelperExceptionSafe(Class c) {
    try {
      return AntInstrospector.getInstance(c);
    }
    catch (Throwable e) {
      // can't be
    }
    return null;
  }

  private static final class ReflectedProject {

    @NonNls private static final String INIT_METHOD_NAME = "init";
    @NonNls private static final String GET_TASK_DEFINITIONS_METHOD_NAME = "getTaskDefinitions";
    @NonNls private static final String GET_DATA_TYPE_DEFINITIONS_METHOD_NAME = "getDataTypeDefinitions";
    @NonNls private static final String GET_PROPERTIES_METHOD_NAME = "getProperties";


    private static final List<SoftReference<Pair<ReflectedProject, ClassLoader>>> ourProjects =
      new ArrayList<SoftReference<Pair<ReflectedProject, ClassLoader>>>();
    private static Alarm ourAlarm = new Alarm();

    private Object myProject;
    private Hashtable myTaskDefinitions;
    private Hashtable myDataTypeDefinitions;
    private Hashtable myProperties;
    private Class myTargetClass;

    private static ReflectedProject getProject(final ClassLoader classLoader) {
      try {
        synchronized (ourProjects) {
          for (final SoftReference<Pair<ReflectedProject, ClassLoader>> ref : ourProjects) {
            final Pair<ReflectedProject, ClassLoader> pair = ref.get();
            if (pair != null && pair.second == classLoader) {
              return pair.first;
            }
          }
          ReflectedProject project = new ReflectedProject(classLoader);
          final SoftReference<Pair<ReflectedProject, ClassLoader>> ref =
            new SoftReference<Pair<ReflectedProject, ClassLoader>>(new Pair<ReflectedProject, ClassLoader>(project, classLoader));
          for (int i = 0; i < ourProjects.size(); ++i) {
            final Pair<ReflectedProject, ClassLoader> pair = ourProjects.get(i).get();
            if (pair == null) {
              ourProjects.set(i, ref);
              return project;
            }
          }
          ourProjects.add(ref);
          return project;
        }
      }
      finally {
        ourAlarm.cancelAllRequests();
        ourAlarm.addRequest(new Runnable() {
          public void run() {
            synchronized (ourProjects) {
              ourProjects.clear();
            }
          }
        }, 30000, ModalityState.NON_MODAL);
      }
    }

    private ReflectedProject(final ClassLoader classLoader) {
      myProject = null;
      try {
        final Class projectClass = classLoader.loadClass("org.apache.tools.ant.Project");
        if (projectClass != null) {
          myProject = projectClass.newInstance();
          Method method = projectClass.getMethod(INIT_METHOD_NAME);
          method.invoke(myProject);
          method = getMethod(projectClass, GET_TASK_DEFINITIONS_METHOD_NAME);
          myTaskDefinitions = (Hashtable)method.invoke(myProject);
          method = getMethod(projectClass, GET_DATA_TYPE_DEFINITIONS_METHOD_NAME);
          myDataTypeDefinitions = (Hashtable)method.invoke(myProject);
          method = getMethod(projectClass, GET_PROPERTIES_METHOD_NAME);
          myProperties = (Hashtable)method.invoke(myProject);
          myTargetClass = classLoader.loadClass("org.apache.tools.ant.Target");
        }
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
  
  private static class PropertiesWatcher {
    private final List<PsiFile> myDependentFiles;
    private final long[] myStamps;

    public PropertiesWatcher(List<PsiFile> dependentFiles) {
      myDependentFiles = dependentFiles;
      myStamps = new long[dependentFiles.size()];

      int index = 0;
      for (PsiFile file : dependentFiles) {
        myStamps[index++] = file.getModificationStamp();
      }
    }
    
    public boolean needRebuildProperties() {
      int idx = 0;
      for (PsiFile file : myDependentFiles) {
        if (myStamps[idx++] != file.getModificationStamp()) {
          return true;
        }
      }
      return false;
    }
  }
}