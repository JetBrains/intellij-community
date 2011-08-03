/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.getConfiguration().LIBRARY_PROJECT) {
        result.add(module);
      }
    }
    return result.toArray(new Module[result.size()]);
  }
}
