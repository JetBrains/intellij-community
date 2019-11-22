// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection.highlightTemplate;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.*;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author cdr
 */
public class SSBasedInspectionOptions {
  final JBList<Configuration> myTemplatesList;
  // for externalization
  final List<Configuration> myConfigurations;

  public SSBasedInspectionOptions(final List<Configuration> configurations) {
    myConfigurations = configurations;
    myTemplatesList  = new JBList<>(new MyListModel());
    myTemplatesList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final JLabel component = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final Configuration configuration = myConfigurations.get(index);
        component.setText(configuration.getName());
        return component;
      }
    });
  }

  void addTemplate(Configuration configuration, Project project) {
    if (!ConfigurationManager.showSaveTemplateAsDialog(myConfigurations, configuration, project)) {
      return;
    }

    configurationsChanged(project);
  }

  public void configurationsChanged(Project project) {
    ((MyListModel)myTemplatesList.getModel()).fireContentsChanged();
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  public JPanel getComponent() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel(SSRBundle.message("SSRInspection.selected.templates")));
    panel.add(
      ToolbarDecorator.createDecorator(myTemplatesList)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            final AnAction[] children = new AnAction[]{new AddTemplateAction(false), new AddTemplateAction(true)};
            JBPopupFactory.getInstance().createActionGroupPopup(null, new DefaultActionGroup(children),
                                                                DataManager.getInstance().getDataContext(button.getContextComponent()),
                                                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
              .show(button.getPreferredPopupPoint());
          }
        }).setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(panel));
          if (project != null && !DumbService.isDumb(project)) {
            performEditAction(project);
          }
        }
      }).setEditActionUpdater(e -> {
        final Project project = e.getProject();
        return project != null && !DumbService.isDumb(project);
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(panel));
          if (project == null) return;
          for (Configuration configuration : myTemplatesList.getSelectedValuesList()) {
            myConfigurations.remove(configuration);
          }
          configurationsChanged(project);
        }
      }).setRemoveActionUpdater(e -> {
        final Project project = e.getProject();
        return project != null && !DumbService.isDumb(project);
      })
        .setMoveUpAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          performMoveUpDown(false);
        }
      }).setMoveDownAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          performMoveUpDown(true);
        }
      })
        .setPreferredSize(new Dimension(-1, 100))
        .createPanel()
    );
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(panel));
        if (project != null && !DumbService.isDumb(project)) {
          performEditAction(project);
        }
        return true;
      }
    }.installOn(myTemplatesList);
    return panel;
  }

  void performMoveUpDown(boolean down) {
    final int[] indices = myTemplatesList.getSelectedIndices();
    if (indices.length == 0) return;
    final int delta = down ? 1 : -1;
    myTemplatesList.removeSelectionInterval(0, myConfigurations.size() - 1);
    for (int i = down ? indices.length - 1 : 0; down ? i >= 0 : i < indices.length; i -= delta) {
      final int index = indices[i];
      final Configuration temp = myConfigurations.get(index);
      myConfigurations.set(index, myConfigurations.get(index + delta));
      myConfigurations.set(index + delta, temp);
      myTemplatesList.addSelectionInterval(index + delta, index + delta);
    }
    final int index = down ? myTemplatesList.getMaxSelectionIndex() : myTemplatesList.getMinSelectionIndex();
    final Rectangle cellBounds = myTemplatesList.getCellBounds(index, index);
    if (cellBounds != null) {
      myTemplatesList.scrollRectToVisible(cellBounds);
    }
  }

  void performEditAction(Project project) {
    final Configuration configuration = myTemplatesList.getSelectedValue();
    if (configuration == null) {
      return;
    }
    final SearchContext searchContext = new SearchContext(project);
    final StructuralSearchDialog dialog = new StructuralSearchDialog(searchContext, !(configuration instanceof SearchConfiguration), true);
    dialog.loadConfiguration(configuration);
    dialog.setUseLastConfiguration(true);
    if (!dialog.showAndGet()) return;
    final Configuration newConfiguration = dialog.getConfiguration();
    final int index = myConfigurations.indexOf(configuration);
    myConfigurations.set(index, newConfiguration);
    configurationsChanged(project);
  }

  private class MyListModel extends AbstractListModel<Configuration> {
    @Override
    public int getSize() {
      return myConfigurations.size();
    }

    @Override
    public Configuration getElementAt(int index) {
      return index < myConfigurations.size() ? myConfigurations.get(index) : null;
    }

    public void fireContentsChanged() {
      fireContentsChanged(myTemplatesList, -1, -1);
    }
  }

  private class AddTemplateAction extends AnAction {

    private final boolean myReplace;

    AddTemplateAction(boolean replace) {
      super(replace
            ? SSRBundle.message("SSRInspection.add.replace.template.button")
            : SSRBundle.message("SSRInspection.add.search.template.button"));
      myReplace = replace;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final SearchContext context = new SearchContext(e.getDataContext());
      final StructuralSearchDialog dialog = new StructuralSearchDialog(context, myReplace, true);
      if (!dialog.showAndGet()) return;
      addTemplate(dialog.getConfiguration(), e.getProject());
    }
  }
}
