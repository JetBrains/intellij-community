package com.intellij.appengine.util;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.facet.FacetManager;
import com.intellij.javaee.artifact.JavaeeArtifactUtil;
import com.intellij.javaee.web.artifact.WebArtifactUtil;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineUtil {
  public static final Icon APP_ENGINE_ICON = IconLoader.getIcon("/icons/appEngine.png");
  @NonNls public static final String APP_ENGINE_WEB_XML_NAME = "appengine-web.xml";
  @NonNls public static final String JDO_CONFIG_XML_NAME = "jdoconfig.xml";
  @NonNls public static final String JPA_CONFIG_XML_NAME = "persistence.xml";

  private AppEngineUtil() {
  }

  public static void setupAppEngineArtifactCombobox(@NotNull Project project, final @NotNull JComboBox comboBox, final boolean withAppEngineFacetOnly) {
    comboBox.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof Artifact) {
          final Artifact artifact = (Artifact)value;
          setIcon(artifact.getArtifactType().getIcon());
          setText(artifact.getName());
        }
        return renderer;
      }
    });

    comboBox.removeAllItems();
    for (Artifact artifact : collectWebArtifacts(project, withAppEngineFacetOnly)) {
      comboBox.addItem(artifact);
    }
  }

  public static List<Artifact> collectWebArtifacts(@NotNull Project project, final boolean withAppEngineFacetOnly) {
    final List<Artifact> artifacts = new ArrayList<Artifact>();
    for (Artifact artifact : ArtifactManager.getInstance(project).getArtifactsByType(WebArtifactUtil.getInstance().getExplodedWarArtifactType())) {
      if (!withAppEngineFacetOnly || findAppEngineFacet(project, artifact) != null) {
        artifacts.add(artifact);
      }
    }
    Collections.sort(artifacts, ArtifactManager.ARTIFACT_COMPARATOR);
    return artifacts;
  }

  @Nullable
  public static AppEngineFacet findAppEngineFacet(@NotNull Project project, @NotNull Artifact artifact) {
    final Collection<WebFacet> facets = JavaeeArtifactUtil.getInstance().getFacetsIncludedInArtifact(project, artifact, WebFacet.ID);
    for (WebFacet webFacet : facets) {
      final AppEngineFacet appEngineFacet = FacetManager.getInstance(webFacet.getModule()).getFacetByType(webFacet, AppEngineFacet.ID);
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
    List<String> paths = new ArrayList<String>();
    for (String url : rootModel.getSourceRootUrls(false)) {
      paths.add(VfsUtil.urlToPath(url));
    }
    return paths;
  }
}
