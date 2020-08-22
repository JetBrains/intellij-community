// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.gradle.model.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.gradle.model.JpsGradleExtensionService;
import org.jetbrains.jps.gradle.model.JpsGradleModuleExtension;
import org.jetbrains.jps.incremental.resources.ResourcesBuilder;
import org.jetbrains.jps.incremental.resources.StandardResourceBuilderEnabler;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
public final class JpsGradleExtensionServiceImpl extends JpsGradleExtensionService {
  private static final Logger LOG = Logger.getInstance(JpsGradleExtensionServiceImpl.class);
  private static final JpsElementChildRole<JpsSimpleElement<Boolean>> PRODUCTION_ON_TEST_ROLE = JpsElementChildRoleBase.create("gradle production on test");
  private final Map<File, GradleProjectConfiguration> myLoadedConfigs = FileCollectionFactory.createCanonicalFileMap();
  private final Map<File, Boolean> myConfigFileExists = ConcurrentFactoryMap.createMap(key -> key.exists());

  public JpsGradleExtensionServiceImpl() {
    ResourcesBuilder.registerEnabler(new StandardResourceBuilderEnabler() {
      @Override
      public boolean isResourceProcessingEnabled(JpsModule module) {
        // enable standard resource processing only if this is not a gradle module
        // for gradle modules use gradle-aware resource builder
        return getExtension(module) == null;
      }
    });
  }

  @Nullable
  @Override
  public JpsGradleModuleExtension getExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(JpsGradleModuleExtensionImpl.ROLE);
  }

  @Override
  @NotNull
  public JpsGradleModuleExtension getOrCreateExtension(@NotNull JpsModule module, @Nullable String moduleType) {
    JpsGradleModuleExtension extension = module.getContainer().getChild(JpsGradleModuleExtensionImpl.ROLE);
    if (extension == null) {
      extension = new JpsGradleModuleExtensionImpl(moduleType);
      module.getContainer().setChild(JpsGradleModuleExtensionImpl.ROLE, extension);
    }
    return extension;
  }

  @Override
  public void setProductionOnTestDependency(@NotNull JpsDependencyElement dependency, boolean value) {
    if (value) {
      dependency.getContainer().setChild(PRODUCTION_ON_TEST_ROLE, JpsElementFactory.getInstance().createSimpleElement(true));
    }
    else {
      dependency.getContainer().removeChild(PRODUCTION_ON_TEST_ROLE);
    }
  }

  @Override
  public boolean isProductionOnTestDependency(@NotNull JpsDependencyElement dependency) {
    JpsSimpleElement<Boolean> child = dependency.getContainer().getChild(PRODUCTION_ON_TEST_ROLE);
    return child != null && child.getData();
  }

  @Override
  public boolean hasGradleProjectConfiguration(@NotNull BuildDataPaths paths) {
    return myConfigFileExists.get(new File(paths.getDataStorageRoot(), GradleProjectConfiguration.CONFIGURATION_FILE_RELATIVE_PATH));
  }

  @NotNull
  @Override
  public GradleProjectConfiguration getGradleProjectConfiguration(BuildDataPaths paths) {
    final File dataStorageRoot = paths.getDataStorageRoot();
    return getGradleProjectConfiguration(dataStorageRoot);
  }

  @NotNull
  public GradleProjectConfiguration getGradleProjectConfiguration(@NotNull File dataStorageRoot) {
    final File configFile = new File(dataStorageRoot, GradleProjectConfiguration.CONFIGURATION_FILE_RELATIVE_PATH);
    GradleProjectConfiguration config;
    synchronized (myLoadedConfigs) {
      config = myLoadedConfigs.get(configFile);
      if (config == null) {
        config = new GradleProjectConfiguration();
        if (configFile.exists()) {
          try {
            XmlSerializer.deserializeInto(config, JDOMUtil.load(configFile));
          }
          catch (Exception e) {
            LOG.info(e);
          }
        }
        myLoadedConfigs.put(configFile, config);
      }
    }
    return config;
  }
}
