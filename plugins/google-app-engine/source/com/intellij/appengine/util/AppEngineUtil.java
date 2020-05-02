// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.util;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.facet.AppEngineWebIntegration;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AppEngineUtil {
  @NonNls public static final String APP_ENGINE_WEB_XML_NAME = "appengine-web.xml";
  @NonNls public static final String APP_ENGINE_APPLICATION_XML_NAME = "appengine-application.xml";
  @NonNls public static final String JDO_CONFIG_XML_NAME = "jdoconfig.xml";
  @NonNls public static final String JPA_CONFIG_XML_NAME = "persistence.xml";

  private AppEngineUtil() {
  }

  public static void setupAppEngineArtifactCombobox(@NotNull Project project, final @NotNull JComboBox<Artifact> comboBox, final boolean withAppEngineFacetOnly) {
    comboBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      if (value != null) {
        label.setIcon(value.getArtifactType().getIcon());
        label.setText(value.getName());
      }
    }));

    comboBox.removeAllItems();
    for (Artifact artifact : collectAppEngineArtifacts(project, withAppEngineFacetOnly)) {
      comboBox.addItem(artifact);
    }
  }

  public static List<Artifact> collectAppEngineArtifacts(@NotNull Project project, final boolean withAppEngineFacetOnly) {
    final List<Artifact> artifacts = new ArrayList<>();
    if (project.isDefault()) return artifacts;
    for (Artifact artifact : ArtifactManager.getInstance(project).getArtifacts()) {
      if (AppEngineWebIntegration.getInstance().getAppEngineTargetArtifactTypes().contains(artifact.getArtifactType()) &&
          (!withAppEngineFacetOnly || findAppEngineFacet(project, artifact) != null)) {
        artifacts.add(artifact);
      }
    }
    artifacts.sort(ArtifactManager.ARTIFACT_COMPARATOR);
    return artifacts;
  }

  @Nullable
  public static AppEngineFacet findAppEngineFacet(@NotNull Project project, @NotNull Artifact artifact) {
    final Set<Module> modules = ArtifactUtil.getModulesIncludedInArtifacts(Collections.singletonList(artifact), project);
    for (Module module : modules) {
      final AppEngineFacet appEngineFacet = AppEngineFacet.getAppEngineFacetByModule(module);
      if (appEngineFacet != null) {
        return appEngineFacet;
      }
    }
    return null;
  }

  public static File getAppEngineSystemDir() {
    return new File(PathManager.getSystemPath(), "GoogleAppEngine");
  }

  public static List<String> getDefaultSourceRootsToEnhance(ModuleRootModel rootModel) {
    List<String> paths = new ArrayList<>();
    for (String url : rootModel.getSourceRootUrls(false)) {
      paths.add(VfsUtil.urlToPath(url));
    }
    return paths;
  }
}
