/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.ant.model;

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
import org.jetbrains.jps.model.serialization.JpsGlobalLoader;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.util.Collections;

/**
 * @author nik
 */
public class JpsAntExtensionService {
  private static final Logger LOG = Logger.getInstance(JpsAntExtensionService.class);

  @Nullable
  public static JpsAntArtifactExtension getPreprocessingExtension(@NotNull JpsArtifact artifact) {
    return artifact.getContainer().getChild(JpsAntArtifactExtensionImpl.PREPROCESSING_ROLE);
  }

  @Nullable
  public static JpsAntArtifactExtension getPostprocessingExtension(@NotNull JpsArtifact artifact) {
    return artifact.getContainer().getChild(JpsAntArtifactExtensionImpl.POSTPROCESSING_ROLE);
  }

  public static void addAntInstallation(JpsGlobal global, JpsAntInstallation antInstallation) {
    global.getContainer().getOrSetChild(JpsAntInstallationImpl.COLLECTION_ROLE).addChild(antInstallation);
  }

  @NotNull
  public static JpsAntBuildFileOptions getOptions(@NotNull JpsProject project, @NotNull String buildFileUrl) {
    JpsAntConfiguration configuration = getAntConfiguration(project);
    if (configuration != null) {
      return configuration.getOptions(buildFileUrl);
    }
    return new JpsAntBuildFileOptionsImpl();
  }

  @Nullable
  private static JpsAntConfiguration getAntConfiguration(JpsProject project) {
    return project.getContainer().getChild(JpsAntConfigurationImpl.ROLE);
  }

  @Nullable
  private static JpsAntInstallation getBundledAntInstallation(@NotNull JpsGlobal global) {
    String appHome = JpsGlobalLoader.getPathVariable(global, PathMacroUtil.APPLICATION_HOME_DIR);
    if (appHome == null) {
      LOG.debug(PathMacroUtil.APPLICATION_HOME_DIR + " path variable not found, bundled Ant won't be configured");
      return null;
    }

    File antHome = new File(appHome, "lib" + File.separator + "ant");
    if (!antHome.exists()) {
      File communityAntHome = new File(appHome, "community" + File.separator + "lib" + File.separator + "ant");
      if (communityAntHome.exists()) {
        antHome = communityAntHome;
      }
    }
    if (!antHome.exists()) {
      LOG.debug("Bundled Ant not found at " + antHome.getAbsolutePath());
      return null;
    }

    String antLib = new File(antHome, "lib").getAbsolutePath();
    return new JpsAntInstallationImpl(antHome, "Bundled Ant", Collections.<String>emptyList(), Collections.singletonList(antLib));
  }

  @Nullable
  public static JpsAntInstallation getAntInstallationForBuildFile(@NotNull JpsModel model, @NotNull String buildFileUrl) {
    JpsAntBuildFileOptions options = getOptions(model.getProject(), buildFileUrl);
    String antInstallationName;
    if (options.isUseProjectDefaultAnt()) {
      JpsAntConfiguration antConfiguration = getAntConfiguration(model.getProject());
      antInstallationName = antConfiguration != null ? antConfiguration.getProjectDefaultAntName() : null;
    }
    else {
      antInstallationName = options.getAntInstallationName();
    }

    if (antInstallationName == null) return getBundledAntInstallation(model.getGlobal());

    return findAntInstallation(model, antInstallationName);
  }

  @Nullable
  public static JpsAntInstallation findAntInstallation(@NotNull JpsModel model, @NotNull String antInstallationName) {
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
