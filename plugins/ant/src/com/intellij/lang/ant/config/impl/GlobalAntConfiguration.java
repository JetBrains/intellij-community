// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl;

import com.intellij.ide.macro.MacroManager;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.config.*;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@State(name = "GlobalAntConfiguration", storages = @Storage(StoragePathMacros.NON_ROAMABLE_FILE))
public final class GlobalAntConfiguration implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(GlobalAntConfiguration.class);

  public static final StorageProperty FILTERS_TABLE_LAYOUT = new StorageProperty("filtersTableLayout");
  public static final StorageProperty PROPERTIES_TABLE_LAYOUT = new StorageProperty("propertiesTableLayout");
  private static final ListProperty<AntInstallation> ANTS = ListProperty.create("registeredAnts");
  private final ExternalizablePropertyContainer myProperties = new ExternalizablePropertyContainer();
  private final AntInstallation myBundledAnt;
  public final Condition<AntInstallation> IS_USER_ANT = new Condition<>() {
    @Override
    public boolean value(AntInstallation antInstallation) {
      return antInstallation != myBundledAnt;
    }
  };

  public static final AbstractProperty<GlobalAntConfiguration> INSTANCE = new ValueProperty<>(
    "$GlobalAntConfiguration.INSTANCE", null);
  public static final @NonNls String ANT_FILE = "ant";
  public static final @NonNls String LIB_DIR = "lib";
  private static final @NonNls String ANT_JAR_FILE_NAME = "ant.jar";

  public GlobalAntConfiguration() {
    myProperties.registerProperty(FILTERS_TABLE_LAYOUT, Externalizer.STORAGE);
    myProperties.registerProperty(PROPERTIES_TABLE_LAYOUT, Externalizer.STORAGE);
    myProperties.registerProperty(ANTS, ANT_FILE, AntInstallation.EXTERNALIZER);
    INSTANCE.set(myProperties, this);
    myProperties.rememberKey(INSTANCE);

    myBundledAnt = createBundledAnt();
  }

  public static AntInstallation createBundledAnt() {
    AntInstallation bundledAnt = new AntInstallation() {
      @Override
      public AntReference getReference() {
        return AntReference.BUNDLED_ANT;
      }
    };
    AntInstallation.NAME.set(bundledAnt.getProperties(), getBundledAntName());

    File antHome = getBundledAntHome();

    AntInstallation.HOME_DIR.set(bundledAnt.getProperties(), antHome.getAbsolutePath());
    ArrayList<AntClasspathEntry> classpath = AntInstallation.CLASS_PATH.getModifiableList(bundledAnt.getProperties());
    File antLibDir = new File(antHome, LIB_DIR);

    File compiledJar = new File(antHome, ANT_JAR_FILE_NAME);
    if (compiledJar.exists()) {
      classpath.add(new SinglePathEntry(compiledJar));
      bundledAnt.updateVersion(compiledJar);
    }
    else {
      classpath.add(new AllJarsUnderDirEntry(antLibDir));
      bundledAnt.updateVersion(new File(antLibDir, ANT_JAR_FILE_NAME));
    }

    return bundledAnt;
  }

  public static @NotNull File getBundledAntHome() {
    File distDir = PluginPathManager.getPluginResource(AntInstallation.class, "dist");
    if (distDir != null && distDir.exists()) {
      return distDir;
    }

    // probably running from source
    File antFile = new File(PathManager.getLibPath(), ANT_FILE);
    if (!antFile.exists()) {
      antFile = new File(PathManager.getHomePath() + "/community/lib", ANT_FILE);
    }
    if (antFile.exists()) {
      return antFile;
    }

    throw new IllegalStateException("Unable to find bundled Ant");
  }

  @Override
  public @NotNull Element getState() {
    Element element = new Element("state");
    myProperties.writeExternal(element);
    return element;
  }

  @Override
  public void loadState(@NotNull Element state) {
    myProperties.readExternal(state);
  }

  public static GlobalAntConfiguration getInstance() {
    return ApplicationManager.getApplication().getService(GlobalAntConfiguration.class);
  }

  public Map<AntReference, AntInstallation> getConfiguredAnts() {
    Map<AntReference, AntInstallation> map = new HashMap<>(ContainerUtil.newMapFromValues(ANTS.getIterator(getProperties()),
                                                                                          AntInstallation.REFERENCE_TO_ANT));
    map.put(AntReference.BUNDLED_ANT, myBundledAnt);
    return map;
  }

  public AntInstallation getBundledAnt() {
    return myBundledAnt;
  }

  public AbstractProperty.AbstractPropertyContainer<?> getProperties() {
    return myProperties;
  }

  public void addConfiguration(final AntInstallation ant) {
    if (getConfiguredAnts().containsKey(ant.getReference())) {
      LOG.error("Duplicate name: " + ant.getName());
    }
    ANTS.getModifiableList(getProperties()).add(ant);
  }

  public void removeConfiguration(final AntInstallation ant) {
    ANTS.getModifiableList(getProperties()).remove(ant);
  }

  public static Sdk findJdk(final String jdkName) {
    return ProjectJdkTable.getInstance().findJdk(jdkName);
  }

  public static MacroManager getMacroManager() {
    return MacroManager.getInstance();
  }

  public AntBuildTarget findTarget(@Nullable Project project, String fileUrl, String targetName) {
    if (fileUrl == null || targetName == null || project == null) {
      return null;
    }
    final VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(fileUrl);
    if (vFile == null) {
      return null;
    }
    final AntConfigurationImpl antConfiguration = (AntConfigurationImpl)AntConfiguration.getInstance(project);
    for (AntBuildFile buildFile : antConfiguration.getBuildFileList()) {
      if (vFile.equals(buildFile.getVirtualFile())) {
        final AntBuildTarget target = buildFile.getModel().findTarget(targetName);
        if (target != null) {
          return target;
        }
        for (AntBuildTarget metaTarget : antConfiguration.getMetaTargets(buildFile)) {
          if (targetName.equals(metaTarget.getName())) {
            return metaTarget;
          }
        }
        return null;
      }
    }
    return null;
  }

  public static @Nls String getBundledAntName() {
    return AntBundle.message("ant.reference.bundled.ant.name");
  }
}
