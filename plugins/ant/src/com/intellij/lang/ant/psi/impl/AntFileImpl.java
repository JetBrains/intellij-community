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

import com.intellij.extapi.psi.LightPsiFileBase;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.impl.AntBuildFileImpl;
import com.intellij.lang.ant.config.impl.AntConfigurationImpl;
import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import com.intellij.lang.ant.misc.AntStringInterner;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Alarm;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.Processor;
import com.intellij.util.StringBuilderSpinAllocator;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.TaskContainer;
import org.apache.tools.ant.taskdefs.Property;
import org.apache.tools.ant.types.DataType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.util.*;

@SuppressWarnings({"UseOfObsoleteCollectionType"})
public class AntFileImpl extends LightPsiFileBase implements AntFile {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.psi.impl.AntFileImpl");
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
  private Map<String, List<AntProperty>> myProperties;
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
  private long myModificationCount = 0;
  public static final Key ANT_BUILD_FILE = Key.create("ANT_BUILD_FILE");

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
    return getSourceElement().getOriginalFile().getVirtualFile();
  }

  public AntElementRole getRole() {
    return AntElementRole.NULL_ROLE;
  }

  public boolean canRename() {
    return isPhysical();
  }

  public void clearExternalProperties() {
    synchronized (PsiLock.LOCK) {
      myExternalProperties = null;
    }
  }

  public void setExternalProperty(@NotNull final String name, @NotNull final String value) {
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
      buildPropertiesIfNeeded();
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
    synchronized (PsiLock.LOCK) {
      final AntFileImpl copy = new AntFileImpl(viewProvider);

      final HashMap<String, AntTypeDefinition> defs;
      if (myTypeDefinitions != null) {
        defs = new HashMap<String, AntTypeDefinition>();
        for (Map.Entry<String, AntTypeDefinition> entry : myTypeDefinitions.entrySet()) {
          final AntTypeDefinitionImpl original = (AntTypeDefinitionImpl)entry.getValue();
          if (original != null) {
            defs.put(entry.getKey(), new AntTypeDefinitionImpl(original));
          }
        }
      } else {
        defs = null;
      }
      copy.myTypeDefinitions = defs;
      copy.myProjectElements = myProjectElements == null ? null : new HashMap<AntTypeId, String>(myProjectElements);
      copy.myClassLoader = myClassLoader;
      copy.myExternalProperties = myExternalProperties != null? new HashMap<String, String>(myExternalProperties) : null;
      if (myProperties != null) {
        final HashMap<String, List<AntProperty>> map = new HashMap<String, List<AntProperty>>();
        for (Map.Entry<String, List<AntProperty>> entry : myProperties.entrySet()) {
          map.put(entry.getKey(), new ArrayList<AntProperty>(entry.getValue()));
        }
        copy.myProperties = map;
      }
      else {
        copy.myProperties = null;
      }

      return copy;
    }
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
      if (myChildren != null || myPrologElement != null || myProject != null || myEpilogueElement != null || myTargetDefinition != null || myClassLoader != null || myEnvPrefixes != null) {
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

  public void incModificationCount() {
    synchronized (PsiLock.LOCK) {
      ++myModificationCount;
      for (final AntFile file : AntSupport.getImportingFiles(this)) {
        file.clearCaches();
      }
    }
  }

  public long getModificationCount() {
    synchronized (PsiLock.LOCK) {
      return myModificationCount;
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
  public ClassLoader getClassLoader() {
    if (myClassLoader == null) {
      final AntBuildFileImpl buildFile = (AntBuildFileImpl)getSourceElement().getCopyableUserData(ANT_BUILD_FILE);
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
    final AntBuildFileImpl buildFile = (AntBuildFileImpl)getSourceElement().getCopyableUserData(ANT_BUILD_FILE);
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
  public Sdk getTargetJdk() {
    final AntBuildFileImpl buildFile = (AntBuildFileImpl)getSourceElement().getCopyableUserData(ANT_BUILD_FILE);
    if (buildFile == null) {
      return ProjectRootManager.getInstance(getProject()).getProjectJdk();
    }

    String jdkName = AntBuildFileImpl.CUSTOM_JDK_NAME.get(buildFile.getAllOptions());
    if (jdkName == null || jdkName.length() == 0) {
      jdkName = AntConfigurationImpl.DEFAULT_JDK_NAME.get(buildFile.getAllOptions());
    }
    if (jdkName != null && jdkName.length() > 0) {
      return ProjectJdkTable.getInstance().findJdk(jdkName);
    }
    return ProjectRootManager.getInstance(getProject()).getProjectJdk();
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
          final CharSequence fileText = baseFile.getViewProvider().getContents();
          final TextRange tagRange = tag.getTextRange();
          final int projectStart = tagRange.getStartOffset();
          if (projectStart > 0) {
            myPrologElement = new AntOuterProjectElement(this, 0, fileText.subSequence(0, projectStart).toString());
          }
          final int projectEnd = tagRange.getEndOffset();
          if (projectEnd < fileText.length()) {
            myEpilogueElement = new AntOuterProjectElement(this, projectEnd, fileText.subSequence(projectEnd, fileText.length()).toString());
          }
          final AntProjectImpl project = new AntProjectImpl(this, tag, createProjectDefinition());
          myProject = project;
          if (getSourceElement().isPhysical() || myProperties == null) {
            buildPropertiesMap();
          }
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
        //AntChangeVisitor.updateBuildFile(this);
      }
    }
  }              

  @Nullable
  public AntProperty getProperty(final String name) {
    return getPropertyRecursively(name, new HashSet<AntFile>());
  }

  public void processAllProperties(@NonNls String name, Processor<AntProperty> processor) {
    processAllPropertiesRecursively(name, new HashSet<AntFile>(), processor);
  }

  @Nullable
  private AntProperty getPropertyRecursively(final String name, final Set<AntFile> processed) {
    if (name == null || processed.contains(this)) {
      return null;
    }
    processed.add(this);

    try {
      AntProperty antProperty;

      synchronized (PsiLock.LOCK) {
        if (myProperties != null) {
          final List<AntProperty> antProperties = myProperties.get(name);
          antProperty = antProperties != null && !antProperties.isEmpty()? antProperties.get(0) : null;
        }
        else {
          antProperty = null;
        }
      }

      if (antProperty == null) {
        final AntProject antProject = getAntProject();
        if (antProject != null) {
          for (AntFile imported : antProject.getImportedFiles()) {
            antProperty = (imported instanceof AntFileImpl) ? ((AntFileImpl)imported).getPropertyRecursively(name, processed) : imported.getProperty(name);
            if (antProperty != null) {
              break;
            }
          }
        }
      }

      return antProperty;
    }
    finally {
      processed.remove(this);
    }
  }

  private void processAllPropertiesRecursively(final String name, final Set<AntFile> processed, Processor<AntProperty> processor) {
    if (name == null || processed.contains(this)) {
      return;
    }
    processed.add(this);

    try {
      synchronized (PsiLock.LOCK) {
        if (myProperties != null) {
          final List<AntProperty> antProperties = myProperties.get(name);
          if (antProperties != null) {
            for (AntProperty property : antProperties) {
              if (!processor.process(property)) {
                return;
              }
            }
          }
        }
      }

      final AntProject antProject = getAntProject();
      if (antProject != null) {
        for (AntFile imported : antProject.getImportedFiles()) {
          if (imported instanceof AntFileImpl) {
            ((AntFileImpl)imported).processAllPropertiesRecursively(name, processed, processor);
          }
          else {
            imported.processAllProperties(name, processor);
          }
        }
      }
    }
    finally {
      processed.remove(this);
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
    myProperties = new HashMap<String, List<AntProperty>>();
    myPropertiesArray = null;
    loadPredefinedProperties(myProjectProperties, myExternalProperties);
    final List<PsiFile> dependentFiles = PropertiesBuilder.defineProperties(this);
    myDependentFilesWatcher = new PropertiesWatcher(dependentFiles);
  }

  @SuppressWarnings({"UseOfObsoleteCollectionType"})
  private void loadPredefinedProperties(final Hashtable properties, final Map<String, String> externalProps) {
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
      if (file != null && !FileUtil.isAbsolute(basedir)) {
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
      final AntInstallation installation = getAntInstallation();
      
      builder.append("<property name=\"ant.home\" value=\"");
      final String homeDir = installation.getHomeDir();
      if (homeDir != null) {
        builder.append(homeDir.replace(File.separatorChar, '/'));
      }
      builder.append("\"/>");
      builder.append("<property name=\"ant.version\" value=\"");
      builder.append(installation.getVersion());
      builder.append("\"/>");
      builder.append("<property name=\"ant.project.name\" value=\"");
      final String name = antProject.getName();
      builder.append((name == null) ? "" : name);
      builder.append("\"/>");
      builder.append("<property name=\"ant.java.version\" value=\"");
      final Sdk jdkToRunWith = getTargetJdk();
      final String version = jdkToRunWith != null? jdkToRunWith.getVersionString() : null;
      builder.append(version != null? version : SystemInfo.JAVA_VERSION);
      builder.append("\"/>");
      if (file != null) {
        final String path = file.getPath();
        builder.append("<property name=\"ant.file\" value=\"");
        builder.append(path);
        builder.append("\"/>");
        if (name != null) {
          builder.append("<property name=\"ant.file.");
          builder.append(name);
          builder.append("\" value=\"");
          builder.append(path);
          builder.append("\"/>");
        }
      }
      builder.append("</project>");
      final XmlFile xmlFile = (XmlFile)PsiFileFactory.getInstance(getProject())
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
        appendProperty(property.getName(), property);
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
      appendProperty(name, element);
    }
  }

  private void appendProperty(String name, AntProperty element) {
    if (myProperties == null) {
      myProperties = new HashMap<String, List<AntProperty>>();
    }
    List<AntProperty> props = myProperties.get(name);
    if (props == null) {
      myProperties.put(name, props = new ArrayList<AntProperty>());
    }
    props.add(element);
    myPropertiesArray = null;
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
        final List<AntProperty> props = new ArrayList<AntProperty>(myProperties.size());
        for (List<AntProperty> list : myProperties.values()) {
          if (!list.isEmpty()) {
            props.add(list.get(0));
          }
        }
        myPropertiesArray = props.toArray(new AntProperty[props.size()]);
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
      return lookupTypeDefinition(className);
    }
  }

  private void buildTypeDefinitions() {
    if (myTypeDefinitions == null) {
      myTypeDefinitions = new HashMap<String, AntTypeDefinition>();
      myProjectElements = new HashMap<AntTypeId, String>();
      final ReflectedProject reflectedProject = ReflectedProject.getProject(getClassLoader());
      if (reflectedProject.myProject != null) {
        //first, create task definitons
       updateTypeDefinitions(reflectedProject.myTaskDefinitions, true);
       // second, create definitions of data types
       updateTypeDefinitions(reflectedProject.myDataTypeDefinitions, false);
       myProjectProperties = reflectedProject.myProperties;
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
      final String className = def.getClassName();
      if (className.startsWith(AntMacroDefImpl.ANT_MACRODEF_NAME) || className.startsWith(AntPresetDefImpl.ANT_PRESETDEF_NAME)) {
        return false;
      }
      final Class defClass = loader.loadClass(className);
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
      addTypeDefinition(def);
      if (myTargetDefinition != null && myTargetDefinition != def) {
        if (canBeUsedInTarget(def)) {
          myTargetDefinition.registerNestedType(def.getTypeId(), def.getClassName());
        }
      }
    }
  }

  private AntTypeDefinition lookupTypeDefinition(final String className) {
    return myTypeDefinitions.get(className);
  }

  private void addTypeDefinition(final AntTypeDefinition def) {
    myTypeDefinitions.put(def.getClassName(), def);
    myTypeDefinitionArray = null;
  }

  private void removeTypeDefinition(final AntTypeDefinition def) {
    final AntTypeDefinition definition = myTypeDefinitions.remove(def.getClassName());
    if (definition != null) {
      definition.setOutdated(true);
    }
    myTypeDefinitionArray = null;
  }

  public void unregisterCustomType(final AntTypeDefinition def) {
    synchronized (PsiLock.LOCK) {
      if (myTypeDefinitions != null) {
        removeTypeDefinition(def);
      }
      if (myProjectElements != null) {
        final String registeredClassName = myProjectElements.get(def.getTypeId());
        if (registeredClassName != null && registeredClassName.equals(def.getClassName())) {
          myProjectElements.remove(def.getTypeId());
        }
      }
      if (myTargetDefinition != null && myTargetDefinition != def) {
        myTargetDefinition.unregisterNestedType(def.getTypeId());
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
        addTypeDefinition(def);
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
    final AntIntrospector helper = getHelperExceptionSafe(parentClass);
    if (helper != null) {
      final Enumeration nestedEnum = helper.getNestedElements();
      while (nestedEnum.hasMoreElements()) {
        final String nestedElement = (String)nestedEnum.nextElement();
        final Class nestedElementClass = helper.getElementType(nestedElement);
        if (nestedElementClass != null && lookupTypeDefinition(nestedElementClass.getName()) == null) {
          final AntTypeDefinition nestedDef = createTypeDefinition(new AntTypeId(nestedElement), nestedElementClass, false);
          if (nestedDef != null) {
            addTypeDefinition(nestedDef);
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
    return new AntTypeDefinitionImpl(new AntTypeId(PROJECT_TAG), Project.class.getName(), false, false, projectAttrs, myProjectElements);
  }

  @Nullable
  static AntTypeDefinition createTypeDefinition(final AntTypeId id, final Class typeClass, final boolean isTask) {
    final AntIntrospector helper = getHelperExceptionSafe(typeClass);
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
    
    boolean isAllTasksContainer = false;
    final ClassLoader loader = typeClass.getClassLoader();
    try {
      final Class<?> containerClass = loader != null? loader.loadClass(TaskContainer.class.getName()) : TaskContainer.class;
      isAllTasksContainer = containerClass.isAssignableFrom(typeClass);
    }
    catch (ClassNotFoundException ignored) {
    }
    return new AntTypeDefinitionImpl(id, typeClass.getName(), isTask, isAllTasksContainer, attributes, nestedDefinitions, helper.getExtensionPointTypes(), null);
  }
  
  @Nullable
  private static AntIntrospector getHelperExceptionSafe(Class c) {
    try {
      return AntIntrospector.getInstance(c);
    }
    catch (Throwable ignored) {
    }
    return null;
  }

  public Icon getElementIcon(int flags) {
    return getRole().getIcon();
  }

  private static final class ReflectedProject {

    @NonNls private static final String INIT_METHOD_NAME = "init";
    @NonNls private static final String GET_TASK_DEFINITIONS_METHOD_NAME = "getTaskDefinitions";
    @NonNls private static final String GET_DATA_TYPE_DEFINITIONS_METHOD_NAME = "getDataTypeDefinitions";
    @NonNls private static final String GET_PROPERTIES_METHOD_NAME = "getProperties";


    private static final List<SoftReference<Pair<ReflectedProject, ClassLoader>>> ourProjects =
      new ArrayList<SoftReference<Pair<ReflectedProject, ClassLoader>>>();
    private static final Alarm ourAlarm = new Alarm();

    private final Object myProject;
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
      Object myProject = null;
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
        LOG.info(e);
        myProject = null;
      }
      this.myProject = myProject;
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
