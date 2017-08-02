/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.util.module.choose;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

class ChooseModulesDialogUtil {
  private ChooseModulesDialogUtil() {
  }

  static void setupTable(@NotNull JTable table, @NotNull Project project, Runnable onEnter) {
    table.setShowGrid(false);
    table.setTableHeader(null);
    table.setIntercellSpacing(JBUI.emptySize());
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setDefaultRenderer(Module.class, new MyTableCellRenderer(project));
    table.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyChar() == '\n') {
          onEnter.run();
        }
      }
    });
  }

  private static class MyTableCellRenderer implements TableCellRenderer {
    private final JList myList;
    private final Project myProject;
    private final ColoredListCellRenderer myCellRenderer;

    public MyTableCellRenderer(Project project) {
      myProject = project;
      myList = new JBList();
      myCellRenderer = new ColoredListCellRenderer() {
        protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
          final Module module = ((Module)value);
          setIcon(ModuleType.get(module).getIcon());
          append(module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

          final XmlFile pluginXml = PluginModuleType.getPluginXml(module);
          assert pluginXml != null;

          final VirtualFile virtualFile = pluginXml.getVirtualFile();
          assert virtualFile != null;
          final VirtualFile projectPath = myProject.getBaseDir();
          assert projectPath != null;
          if (VfsUtilCore.isAncestor(projectPath, virtualFile, false)) {
            append(" (" + VfsUtilCore.getRelativePath(virtualFile, projectPath, File.separatorChar) + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          } else {
            append(" (" + virtualFile.getPresentableUrl() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
      };
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      return myCellRenderer.getListCellRendererComponent(myList, value, row, isSelected, hasFocus);
    }
  }
}
