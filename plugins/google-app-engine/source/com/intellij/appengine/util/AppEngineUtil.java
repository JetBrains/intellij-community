package com.intellij.appengine.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.facet.FacetManager;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class AppEngineUtil {
  public static void setupWebFacetCombobox(Project project, final JComboBox comboBox) {
    comboBox.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof WebFacet) {
          final WebFacet webFacet = (WebFacet)value;
          setIcon(webFacet.getType().getIcon());
          setText(webFacet.getName() + " (module '" + webFacet.getModule().getName() + "')");
        }
        return renderer;
      }
    });

    comboBox.removeAllItems();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      for (WebFacet webFacet : FacetManager.getInstance(module).getFacetsByType(WebFacet.ID)) {
        comboBox.addItem(webFacet);
      }
    }
  }
}
