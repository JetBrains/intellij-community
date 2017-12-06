/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.ReflectedProject;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.impl.AntBuildFileImpl;
import com.intellij.lang.ant.config.impl.AntConfigurationImpl;
import com.intellij.lang.ant.config.impl.AntInstallation;
import com.intellij.lang.ant.config.impl.GlobalAntConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
@SuppressWarnings({"AbstractClassNeverImplemented"})
@DefinesXml
public abstract class AntDomProject extends AntDomNamedElement implements PropertiesProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.dom.AntDomProject");

  @NonNls public static final String DEFAULT_ENVIRONMENT_PREFIX = "env.";

  private volatile ClassLoader myClassLoader;
  private volatile Map<String, String> myProperties;


  @Attribute("default")
  @Convert(value = AntDomDefaultTargetConverter.class)
  public abstract GenericAttributeValue<TargetResolver.Result> getDefaultTarget();

  @Attribute("basedir")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getBasedir();

  @Nullable
  public final PsiFileSystemItem getProjectBasedir() {
    final PsiFileSystemItem basedir = getBasedir().getValue();
    if (basedir != null) {
      return basedir;
    }
    final XmlTag tag = getXmlTag();
    final VirtualFile containingFile = tag.getContainingFile().getOriginalFile().getVirtualFile();
    if (containingFile == null) {
      return null;
    }
    final VirtualFile parent = containingFile.getParent();
    if (parent == null) {
      return null;
    }
    return tag.getManager().findDirectory(parent);
  }

  @Nullable
  public final String getProjectBasedirPath() {
    final String basedir = getBasedir().getStringValue();
    if (basedir != null) {
      final File file = new File(basedir);
      if (file.isAbsolute()) {
        try {
          return FileUtil.toSystemIndependentName(file.getCanonicalPath());
        }
        catch (IOException e) {
          LOG.info(e);
          return null;
        }
      }
    }
    final String selfDir = getContainingFileDir();
    if (basedir == null) {
      return selfDir;
    }
    // basedir is specified and is relative
    try {
      return FileUtil.toSystemIndependentName(new File(selfDir, basedir).getCanonicalPath());
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }

  @Nullable
  public final String getContainingFileDir() {
    final VirtualFile containingFile = getXmlTag().getContainingFile().getOriginalFile().getVirtualFile();
    if (containingFile == null) {
      return null;
    }
    final VirtualFile parent = containingFile.getParent();
    return parent != null? parent.getPath() : null;
  }

  @SubTagList("target")
  public abstract List<AntDomTarget> getDeclaredTargets();

  @SubTagList("import")
  public abstract List<AntDomImport> getDeclaredImports();

  @SubTagList("include")
  public abstract List<AntDomInclude> getDeclaredIncludes();

  @Nullable
  public final AntDomTarget findDeclaredTarget(String declaredName) {
    for (AntDomTarget target : getDeclaredTargets()) {
      if (declaredName.equals(target.getName().getRawText())) {
        return target;
      }
    }
    return null;
  }

  @NotNull
  public final ClassLoader getClassLoader() {
    ClassLoader loader = myClassLoader;
    if (loader == null) {
      final XmlTag tag = getXmlTag();
      final PsiFile containingFile = tag.getContainingFile();
      final AntBuildFileImpl buildFile = (AntBuildFileImpl)AntConfigurationBase.getInstance(containingFile.getProject()).getAntBuildFile(containingFile);
      if (buildFile != null) {
        loader = buildFile.getClassLoader();
      }
      else {
        AntInstallation antInstallation = getAntInstallation();
        loader = antInstallation.getClassLoader();
      }
      myClassLoader = loader;
    }
    return loader;
  }

  public AntInstallation getAntInstallation() {
    final AntConfigurationBase configuration = AntConfigurationBase.getInstance(getXmlTag().getProject());
    AntInstallation antInstallation = null;
    if (configuration != null) {
      antInstallation = configuration.getProjectDefaultAnt();
    }
    if (antInstallation == null) {
      antInstallation = GlobalAntConfiguration.getInstance().getBundledAnt();
    }
    assert antInstallation != null;
    return antInstallation;
  }

  @Nullable
  public final Sdk getTargetJdk() {
    final XmlTag tag = getXmlTag();
    final PsiFile containingFile = tag.getContainingFile();
    final AntBuildFileImpl buildFile = (AntBuildFileImpl)AntConfigurationBase.getInstance(containingFile.getProject()).getAntBuildFile(containingFile);
    if (buildFile != null) {
      String jdkName = AntBuildFileImpl.CUSTOM_JDK_NAME.get(buildFile.getAllOptions());
      if (StringUtil.isEmptyOrSpaces(jdkName)) {
        jdkName = AntConfigurationImpl.DEFAULT_JDK_NAME.get(buildFile.getAllOptions());
      }
      if (!StringUtil.isEmptyOrSpaces(jdkName)) {
        return ProjectJdkTable.getInstance().findJdk(jdkName);
      }
    }
    return ProjectRootManager.getInstance(tag.getProject()).getProjectSdk();
  }

  @NotNull
  public Iterator<String> getNamesIterator() {
    return getProperties().keySet().iterator();
  }

  @Nullable
  public String getPropertyValue(String propertyName) {
    return getProperties().get(propertyName);
  }

  @Nullable
  public PsiElement getNavigationElement(String propertyName) {
    final DomTarget target = DomTarget.getTarget(this);
    final PsiElement nameElementPsi = target != null ? PomService.convertToPsi(target) : null;
    if (nameElementPsi != null) {
      return nameElementPsi;
    }
    final XmlElement xmlElement = getXmlElement();
    return xmlElement != null? xmlElement.getNavigationElement() : null;
  }

  private Map<String, String> getProperties() {
    Map<String, String> properties = myProperties;
    if (properties == null) {
      final ReflectedProject reflected = ReflectedProject.getProject(getClassLoader());
      Map<String, String> externals = Collections.emptyMap();
      final PsiFile containingFile = getXmlTag().getContainingFile();
      if (containingFile != null) {
        final AntBuildFileImpl buildFile = (AntBuildFileImpl)AntConfigurationBase.getInstance(containingFile.getProject()).getAntBuildFile(containingFile);
        if (buildFile != null) {
          externals = buildFile.getExternalProperties();
        }
      }
      myProperties = (properties = loadPredefinedProperties(reflected.getProperties(), externals));
    }
    return properties;
  }

  @SuppressWarnings({"UseOfObsoleteCollectionType"})
  private Map<String, String> loadPredefinedProperties(final Hashtable properties, final Map<String, String> externalProps) {
    final Map<String, String> destination = new HashMap<>();
    if (properties != null) {
      final Enumeration props = properties.keys();
      while (props.hasMoreElements()) {
        final String name = (String)props.nextElement();
        final String value = (String)properties.get(name);
        appendProperty(destination, name, value);
      }
    }
    //final Map<String, String> envMap = System.getenv();
    //for (final String name : envMap.keySet()) {
    //  if (name.length() > 0) {
    //    final String value = envMap.get(name);
    //    appendProperty(destination, DEFAULT_ENVIRONMENT_PREFIX + name, value);
    //  }
    //}
    if (externalProps != null) {
      for (final String name : externalProps.keySet()) {
        final String value = externalProps.get(name);
        appendProperty(destination, name, value);
      }
    }

    String basedir = getProjectBasedirPath();
    if (basedir == null) {
      basedir = ".";
    }
    if (!FileUtil.isAbsolute(basedir)) {
      final String containigFileDir = getContainingFileDir();
      if (containigFileDir != null) {
        try {
          basedir = new File(containigFileDir, basedir).getCanonicalPath();
        }
        catch (IOException ignored) {
        }
      }
    }
    appendProperty(destination, "basedir", FileUtil.toSystemIndependentName(basedir));

    final AntInstallation installation = getAntInstallation();
    final String homeDir = installation.getHomeDir();
    if (homeDir != null) {
      appendProperty(destination, "ant.home", FileUtil.toSystemIndependentName(homeDir));
    }
    appendProperty(destination, "ant.version", installation.getVersion());

    final String projectName = getName().getRawText();
    appendProperty(destination, "ant.project.name", (projectName == null) ? "" : projectName);

    final Sdk jdkToRunWith = getTargetJdk();
    final String version = jdkToRunWith != null? jdkToRunWith.getVersionString() : null;
    appendProperty(destination, "ant.java.version", version != null? version : SystemInfo.JAVA_VERSION);
    
    final VirtualFile containingFile = getXmlTag().getContainingFile().getOriginalFile().getVirtualFile();
    if (containingFile != null) {
      final String antFilePath = containingFile.getPath();
      appendProperty(destination, "ant.file", antFilePath);
      if (projectName != null) {
        appendProperty(destination, "ant.file." + projectName, antFilePath);
        appendProperty(destination, "ant.file.type." + projectName, "file");
      }
    }
    return destination;
  }

  private static void appendProperty(final Map<String, String> map, String name, String value) {
    final String previous = map.put(name, value);
    if (previous != null) {
      map.put(name, previous);
    }
  }
}
