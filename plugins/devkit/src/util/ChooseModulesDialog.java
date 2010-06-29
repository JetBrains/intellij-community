/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author swr
 */
public class ChooseModulesDialog extends DialogWrapper {
  private final Icon myIcon;
  private final String myMessage;
  private final JTable myView;
  private final List<Module> myCandidateModules;
  private final boolean[] myStates;

  public ChooseModulesDialog(final Project project, List<Module> candidateModules, @NonNls String title) {
    this ( project, candidateModules, title, DevKitBundle.message("select.plugin.modules.to.patch"));
  }

  public ChooseModulesDialog(final Project project, List<Module> candidateModules, @NonNls String title, final String message) {
    super(project, false);
    setTitle(title);

    myCandidateModules = candidateModules;
    myIcon = Messages.getQuestionIcon();
    myMessage = message;
    myView = new JBTable(new AbstractTableModel() {
      public int getRowCount() {
        return myCandidateModules.size();
      }

      public int getColumnCount() {
        return 2;
      }

      public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0;
      }

      public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        myStates[rowIndex] = (Boolean)aValue;
        fireTableCellUpdated(rowIndex, columnIndex);
      }

      public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 0 ? Boolean.class : Module.class;
      }

      public Object getValueAt(int rowIndex, int columnIndex) {
        return columnIndex == 0 ? myStates[rowIndex] : myCandidateModules.get(rowIndex);
      }
    });

    myView.setShowGrid(false);
    myView.setTableHeader(null);
    myView.setIntercellSpacing(new Dimension(0, 0));
    myView.getColumnModel().getColumn(0).setMaxWidth(new JCheckBox().getPreferredSize().width);
    myView.getModel().addTableModelListener(new TableModelListener() {
      public void tableChanged(TableModelEvent e) {
        getOKAction().setEnabled(getSelectedModules().size() > 0);
      }
    });
    myView.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER
                || e.getKeyChar() == '\n') {
          doOKAction();
        }
      }
    });
    myView.setDefaultRenderer(Module.class, new MyTableCellRenderer(project));

    myStates = new boolean[candidateModules.size()];
    Arrays.fill(myStates, true);

    init();
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout(15, 10));
    if (myIcon != null) {
      JLabel iconLabel = new JLabel(myIcon);
      Container container = new Container();
      container.setLayout(new BorderLayout());
      container.add(iconLabel, BorderLayout.NORTH);
      panel.add(container, BorderLayout.WEST);
    }

    JPanel messagePanel = new JPanel(new BorderLayout());
    if (myMessage != null) {
      JLabel textLabel = new JLabel(myMessage);
      textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
      textLabel.setUI(new MultiLineLabelUI());
      messagePanel.add(textLabel, BorderLayout.NORTH);
    }
    panel.add(messagePanel, BorderLayout.CENTER);

    final JBScrollPane jScrollPane = new JBScrollPane();
    jScrollPane.setViewportView(myView);
    jScrollPane.setPreferredSize(new Dimension(300, 80));
    panel.add(jScrollPane, BorderLayout.SOUTH);
    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myView;
  }


  protected JComponent createCenterPanel() {
    return null;
  }

  public List<Module> getSelectedModules() {
    final ArrayList<Module> list = new ArrayList<Module>(myCandidateModules);
    final Iterator<Module> modules = list.iterator();
    for (boolean b : myStates) {
      modules.next();
      if (!b) {
        modules.remove();
      }
    }
    return list;
  }

  private static class MyTableCellRenderer implements TableCellRenderer {
    private final JList myList;
    private final Project myProject;
    private final ColoredListCellRenderer myCellRenderer;

    public MyTableCellRenderer(Project project) {
      myProject = project;
      myList = new JBList();
      myCellRenderer = new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          final Module module = ((Module)value);
          setIcon(module.getModuleType().getNodeIcon(false));
          append(module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);

          final XmlFile pluginXml = PluginModuleType.getPluginXml(module);
          assert pluginXml != null;

          final VirtualFile virtualFile = pluginXml.getVirtualFile();
          assert virtualFile != null;
          final VirtualFile projectPath = myProject.getBaseDir();
          if (VfsUtil.isAncestor(projectPath, virtualFile, false)) {
            append(" (" + VfsUtil.getRelativePath(virtualFile, projectPath, File.separatorChar) + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
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
