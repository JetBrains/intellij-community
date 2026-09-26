// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.ant.model;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ant.model.artifacts.JpsAntArtifactExtension;
import org.jetbrains.jps.ant.model.impl.JpsAntBuildFileOptionsImpl;
import org.jetbrains.jps.ant.model.impl.JpsAntConfigurationImpl;
import org.jetbrains.jps.ant.model.impl.JpsAntInstallationImpl;
import org.jetbrains.jps.ant.model.impl.artifacts.JpsAntArtifactExtensionImpl;
import org.jetbrains.jps.model.JpsElementCollection;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.io.File;
import java.util.HashMap;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public final class JpsAntExtensionService {
  public static final String BUNDLED_ANT_PATH_PROPERTY = "jps.bundled.ant.path";
  private static final Logger LOG = Logger.getInstance(JpsAntExtensionService.class);

  public static @Nullable JpsAntArtifactExtension getPreprocessingExtension(@NotNull JpsArtifact artifact) {
    return artifact.getContainer().getChild(JpsAntArtifactExtensionImpl.PREPROCESSING_ROLE);
  }

  public static @Nullable JpsAntArtifactExtension getPostprocessingExtension(@NotNull JpsArtifact artifact) {
    return artifact.getContainer().getChild(JpsAntArtifactExtensionImpl.POSTPROCESSING_ROLE);
  }

  public static void addAntInstallation(JpsGlobal global, JpsAntInstallation antInstallation) {
    global.getContainer().getOrSetChild(JpsAntInstallationImpl.COLLECTION_ROLE).addChild(antInstallation);
  }

  public static @NotNull JpsAntBuildFileOptions getOptions(@NotNull JpsProject project, @NotNull String buildFileUrl) {
    JpsAntConfiguration configuration = getAntConfiguration(project);
    if (configuration != null) {
      return configuration.getOptions(buildFileUrl);
    }
    return new JpsAntBuildFileOptionsImpl();
  }

  private static @Nullable JpsAntConfiguration getAntConfiguration(JpsProject project) {
    return project.getContainer().getChild(JpsAntConfigurationImpl.ROLE);
  }

  public static @NotNull JpsAntConfiguration getOrCreateAntConfiguration(@NotNull JpsProject project) {
    JpsAntConfiguration configuration = getAntConfiguration(project);
    if (configuration != null) {
      return configuration;
    }
    JpsAntConfigurationImpl antConfiguration = new JpsAntConfigurationImpl(new HashMap<>(), null);
    return project.getContainer().setChild(JpsAntConfigurationImpl.ROLE, antConfiguration);
  }

  private static @Nullable JpsAntInstallation getBundledAntInstallation() {
    String antPath = System.getProperty(BUNDLED_ANT_PATH_PROPERTY);
    File antHome;
    if (antPath != null) {
      LOG.info("Using bundled Ant " + antPath);

      antHome = new File(antPath);
      if (new File(antHome, "ant.jar").exists()) {
        // everything is packed to a single ./dist/ant.jar
        return bundledAnt(antHome, antHome.getAbsolutePath());
      }
    }
    else {
      String appHome = PathManager.getHomePath(false);
      if (appHome == null) {
        LOG.debug("idea.home.path and " + BUNDLED_ANT_PATH_PROPERTY + " aren't specified, bundled Ant won't be configured");
        return null;
      }

      antHome = new File(appHome, "lib" + File.separator + "ant");
      if (!antHome.exists()) {
        File communityAntHome = new File(appHome, "community" + File.separator + "lib" + File.separator + "ant");
        if (communityAntHome.exists()) {
          antHome = communityAntHome;
        }
      }
    }

    return bundledAnt(antHome, new File(antHome, "lib").getAbsolutePath());
  }

  private static @Nullable JpsAntInstallationImpl bundledAnt(File antHome, String antJarsHome) {
    if (!antHome.exists()) {
      LOG.debug("Bundled Ant not found at " + antHome.getAbsolutePath());
      return null;
    }

    return new JpsAntInstallationImpl(antHome, "Bundled Ant", emptyList(), singletonList(antJarsHome));
  }

  public static @Nullable JpsAntInstallation getAntInstallationForBuildFile(@NotNull JpsModel model, @NotNull String buildFileUrl) {
    JpsAntBuildFileOptions options = getOptions(model.getProject(), buildFileUrl);
    String antInstallationName;
    if (options.isUseProjectDefaultAnt()) {
      JpsAntConfiguration antConfiguration = getAntConfiguration(model.getProject());
      antInstallationName = antConfiguration != null ? antConfiguration.getProjectDefaultAntName() : null;
    }
    else {
      antInstallationName = options.getAntInstallationName();
    }

    if (antInstallationName == null) return getBundledAntInstallation();

    return findAntInstallation(model, antInstallationName);
  }

  public static @Nullable JpsAntInstallation findAntInstallation(@NotNull JpsModel model, @NotNull String antInstallationName) {
    JpsElementCollection<JpsAntInstallation> antInstallations = model.getGlobal().getContainer().getChild(JpsAntInstallationImpl.COLLECTION_ROLE);
    if (antInstallations != null) {
      for (JpsAntInstallation installation : antInstallations.getElements()) {
        if (antInstallationName.equals(installation.getName())) {
          return installation;
        }
      }
      LOG.debug("Ant installation '" + antInstallationName + "' not found");
    }
    else {
      LOG.debug("Ant installations weren't loaded");
    }
    return null;
  }
}
