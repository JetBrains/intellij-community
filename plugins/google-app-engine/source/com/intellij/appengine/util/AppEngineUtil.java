package com.intellij.appengine.util;

import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.facet.FacetManager;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class AppEngineUtil {
  public static final Icon APP_ENGINE_ICON = IconLoader.getIcon("/icons/appEngine.png");
  @NonNls public static final String APPENGINE_WEB_XML_NAME = "appengine-web.xml";

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
}
