package org.jetbrains.android.newProject;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidModulesComboBox extends JComboBox {

  public AndroidModulesComboBox() {
    setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof Module) {
          final Module module = (Module)value;
          setText(module.getName());
          setIcon(module.getModuleType().getNodeIcon(false));
        }
        else {
          setText("<html><font color='red'>[none]</font></html>");
        }
        return this;
      }
    });
  }

  public void init(@NotNull Project project) {
    Module[] modules = getModulesWithAndroidFacet(project);
    setModel(new DefaultComboBoxModel(modules));
  }

  public Module getModule() {
    return (Module)getSelectedItem();
  }

  private static Module[] getModulesWithAndroidFacet(Project project) {
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    final Module[] modules = moduleManager.getModules();
    List<Module> result = new ArrayList<Module>();
    for (Module module : modules) {
      if (AndroidFacet.getInstance(module) != null) {
        result.add(module);
      }
    }
    return result.toArray(new Module[result.size()]);
  }
}
