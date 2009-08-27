package com.intellij.appengine.util;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.facet.FacetManager;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VfsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

/**
 * @author nik
 */
public class AppEngineUtil {
  public static final Icon APP_ENGINE_ICON = IconLoader.getIcon("/icons/appEngine.png");
  @NonNls public static final String APPENGINE_WEB_XML_NAME = "appengine-web.xml";
  @NonNls public static final String JDO_CONFIG_XML_NAME = "jdoconfig.xml";
  @NonNls public static final String JPA_CONFIG_XML_NAME = "persistence.xml";

  private AppEngineUtil() {
  }

  public static void setupAppEngineFacetCombobox(Project project, final JComboBox comboBox) {
    comboBox.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof AppEngineFacet) {
          final AppEngineFacet appEngineFacet = (AppEngineFacet)value;
          final WebFacet webFacet = appEngineFacet.getWebFacet();
          setIcon(webFacet.getType().getIcon());
          setText(webFacet.getName() + " (module '" + webFacet.getModule().getName() + "')");
        }
        return renderer;
      }
    });

    comboBox.removeAllItems();
    for (AppEngineFacet facet : collectAllFacets(project)) {
      comboBox.addItem(facet);
    }
  }

  public static List<? extends AppEngineFacet> collectAllFacets(@NotNull Project project) {
    final List<AppEngineFacet> facets = new ArrayList<AppEngineFacet>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      for (AppEngineFacet facet : FacetManager.getInstance(module).getFacetsByType(AppEngineFacet.ID)) {
        facets.add(facet);
      }
    }
    return facets;
  }

  public static File getAppEngineSystemDir() {
    return new File(PathManager.getSystemPath(), "GoogleAppEngine");
  }

  public static List<String> getDefaultSourceRootsToEnhance(ModuleRootModel rootModel) {
    List<String> paths = new ArrayList<String>();
    for (ContentEntry contentEntry : rootModel.getContentEntries()) {
      for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
        if (!sourceFolder.isTestSource()) {
          paths.add(VfsUtil.urlToPath(sourceFolder.getUrl()));
        }
      }
    }
    return paths;
  }
}
