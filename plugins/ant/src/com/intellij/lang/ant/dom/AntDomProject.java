// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.NlsSafe;
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
@DefinesXml
public abstract class AntDomProject extends AntDomNamedElement implements PropertiesProvider {
  private static final Logger LOG = Logger.getInstance(AntDomProject.class);

  public static final @NonNls String DEFAULT_ENVIRONMENT_PREFIX = "env.";

  private volatile ClassLoader myClassLoader;
  private volatile Map<String, String> myProperties;


  @Attribute("default")
  @Convert(AntDomDefaultTargetConverter.class)
  public abstract GenericAttributeValue<TargetResolver.Result> getDefaultTarget();

  @Attribute("basedir")
  @Convert(AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getBasedir();

  public final @Nullable PsiFileSystemItem getProjectBasedir() {
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

  public final @Nullable @NlsSafe String getProjectBasedirPath() {
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

  public final @Nullable @NlsSafe String getContainingFileDir() {
    final VirtualFile containingFile = getXmlTag().getContainingFile().getOriginalFile().getVirtualFile();
    if (containingFile == null) {
      return null;
    }
    final VirtualFile parent = containingFile.getParent();
    return parent != null ? parent.getPath() : null;
  }

  @SubTagList("target")
  public abstract List<AntDomTarget> getDeclaredTargets();

  @SubTagList("import")
  public abstract List<AntDomImport> getDeclaredImports();

  @SubTagList("include")
  public abstract List<AntDomInclude> getDeclaredIncludes();

  public final @Nullable AntDomTarget findDeclaredTarget(String declaredName) {
    for (AntDomTarget target : getDeclaredTargets()) {
      if (declaredName.equals(target.getName().getRawText())) {
        return target;
      }
    }
    return null;
  }

  public final @NotNull ClassLoader getClassLoader() {
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
    final AntConfigurationBase configuration = AntConfigurationBase.getInstance(getManager().getProject());
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

  public final @Nullable Sdk getTargetJdk() {
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

  @Override
  public @NotNull Iterator<String> getNamesIterator() {
    return getProperties().keySet().iterator();
  }

  @Override
  public @Nullable String getPropertyValue(String propertyName) {
    return getProperties().get(propertyName);
  }

  @Override
  public @Nullable PsiElement getNavigationElement(String propertyName) {
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

  private Map<String, String> loadPredefinedProperties(final Map<String, String> properties, final Map<String, String> externalProps) {
    final Map<String, String> destination = new HashMap<>();
    if (properties != null) {
      properties.forEach((name, value) -> appendProperty(destination, name, value));
    }
    if (externalProps != null) {
      externalProps.forEach((name, value) -> appendProperty(destination, name, value));
    }

    String basedir = getProjectBasedirPath();
    if (basedir == null) {
      basedir = ".";
    }
    if (!FileUtil.isAbsolute(basedir)) {
      final String containingFileDir = getContainingFileDir();
      if (containingFileDir != null) {
        try {
          basedir = new File(containingFileDir, basedir).getCanonicalPath();
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
    map.putIfAbsent(name, value);
  }
}
