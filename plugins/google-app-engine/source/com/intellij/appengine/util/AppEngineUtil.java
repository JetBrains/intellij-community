package com.intellij.appengine.util;

import com.intellij.facet.FacetManager;
import com.intellij.javaee.web.WebUtil;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.jsp.WebDirectoryElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class AppEngineUtil {
  public static final Icon APP_ENGINE_ICON = IconLoader.getIcon("/icons/appEngine.png");
  @NonNls public static final String APPENGINE_WEB_XML_NAME = "appengine-web.xml";

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

  public static boolean isAppEngineSupportEnabled(@NotNull WebFacet webFacet) {
    final WebDirectoryElement element = WebUtil.getWebUtil().findWebDirectoryElement("/WEB-INF/" + APPENGINE_WEB_XML_NAME, webFacet);
    return element != null;
  }
}
