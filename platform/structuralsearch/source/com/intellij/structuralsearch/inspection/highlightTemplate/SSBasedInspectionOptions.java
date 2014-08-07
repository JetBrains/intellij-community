/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.structuralsearch.inspection.highlightTemplate;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceDialog;
import com.intellij.structuralsearch.plugin.ui.*;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Iterator;
import java.util.List;

/**
 * @author cdr
 */
public class SSBasedInspectionOptions {
  private JBList myTemplatesList;
  // for externalization
  private final List<Configuration> myConfigurations;

  public SSBasedInspectionOptions(final List<Configuration> configurations) {
    myConfigurations = configurations;
    myTemplatesList  = new JBList(new MyListModel());
    myTemplatesList.setCellRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel component = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        Configuration configuration = myConfigurations.get(index);
        component.setText(configuration.getName());
        return component;
      }
    });
  }

  private static void copyConfiguration(final Configuration configuration, final Configuration newConfiguration) {
    @NonNls Element temp = new Element("temp");
    configuration.writeExternal(temp);
    newConfiguration.readExternal(temp);
  }

  interface SearchDialogFactory {
    SearchDialog createDialog(SearchContext searchContext);
  }

  private void addTemplate(SearchDialogFactory searchDialogFactory) {
    SearchDialog dialog = createDialog(searchDialogFactory);
    dialog.show();
    if (!dialog.isOK()) return;
    Configuration configuration = dialog.getConfiguration();

    if (configuration.getName() == null || configuration.getName().equals(SearchDialog.USER_DEFINED)) {
      String name = dialog.showSaveTemplateAsDialog();

      if (name != null) {
        name = ConfigurationManager.findAppropriateName(myConfigurations, name, dialog.getProject());
      }
      if (name == null) return;
      configuration.setName(name);
    }
    myConfigurations.add(configuration);

    configurationsChanged(dialog.getSearchContext());
  }

  private static SearchDialog createDialog(final SearchDialogFactory searchDialogFactory) {
    SearchContext searchContext = createSearchContext();
    return searchDialogFactory.createDialog(searchContext);
  }

  private static SearchContext createSearchContext() {
    AnActionEvent event = new AnActionEvent(null, DataManager.getInstance().getDataContext(),
                                            "", new DefaultActionGroup().getTemplatePresentation(), ActionManager.getInstance(), 0);
    return SearchContext.buildFromDataContext(event.getDataContext());
  }

  public void configurationsChanged(final SearchContext searchContext) {
    ((MyListModel)myTemplatesList.getModel()).fireContentsChanged();
  }

  public JPanel getComponent() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel(SSRBundle.message("SSRInspection.selected.templates")));
    panel.add(
      ToolbarDecorator.createDecorator(myTemplatesList)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            final AnAction[] children = new AnAction[]{
              new AnAction(SSRBundle.message("SSRInspection.add.search.template.button")) {
                @Override
                public void actionPerformed(AnActionEvent e) {
                  addTemplate(new SearchDialogFactory() {
                    public SearchDialog createDialog(SearchContext searchContext) {
                      return new SearchDialog(searchContext, false, false);
                    }
                  });
                }
              },
              new AnAction(SSRBundle.message("SSRInspection.add.replace.template.button")) {
                @Override
                public void actionPerformed(AnActionEvent e) {
                  addTemplate(new SearchDialogFactory() {
                    public SearchDialog createDialog(SearchContext searchContext) {
                      return new ReplaceDialog(searchContext, false, false);
                    }
                  });
                }
              }
            };
            JBPopupFactory.getInstance().createActionGroupPopup(null, new DefaultActionGroup(children),
                                                                DataManager.getInstance()
                                                                  .getDataContext(button.getContextComponent()),
                                                                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
              .show(button.getPreferredPopupPoint());
          }
        }).setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          performEditAction();
        }
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          Object[] selected = myTemplatesList.getSelectedValues();
          for (Object o : selected) {
            Configuration configuration = (Configuration)o;
            Iterator<Configuration> iterator = myConfigurations.iterator();
            while (iterator.hasNext()) {
              Configuration configuration1 = iterator.next();
              if (configuration1.getName().equals(configuration.getName())) {
                iterator.remove();
              }
            }
          }
          configurationsChanged(createSearchContext());
        }
      }).setMoveUpAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          performMoveUpDown(false);
        }
      }).setMoveDownAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          performMoveUpDown(true);
        }
      }).createPanel()
    );
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        performEditAction();
        return true;
      }
    }.installOn(myTemplatesList);
    return panel;
  }

  private void performMoveUpDown(boolean down) {
    final int[] indices = myTemplatesList.getSelectedIndices();
    if (indices.length == 0) return;
    final int delta = down ? 1 : -1;
    myTemplatesList.removeSelectionInterval(0, myConfigurations.size() - 1);
    for (int i = down ? indices[indices.length - 1] : 0;
         down ? i >= 0 : i < indices.length;
         i -= delta) {
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

  private void performEditAction() {
    final Configuration configuration = (Configuration)myTemplatesList.getSelectedValue();
    if (configuration == null) return;

    SearchDialog dialog = createDialog(new SearchDialogFactory() {
      public SearchDialog createDialog(SearchContext searchContext) {
        if (configuration instanceof SearchConfiguration) {
          return new SearchDialog(searchContext, false, false) {
            public Configuration createConfiguration() {
              SearchConfiguration newConfiguration = new SearchConfiguration();
              copyConfiguration(configuration, newConfiguration);
              return newConfiguration;
            }
          };
        }
        else {
          return new ReplaceDialog(searchContext, false, false) {
            public Configuration createConfiguration() {
              ReplaceConfiguration newConfiguration = new ReplaceConfiguration();
              copyConfiguration(configuration, newConfiguration);
              return newConfiguration;
            }
          };
        }
      }
    });
    dialog.setValuesFromConfig(configuration);
    dialog.setUseLastConfiguration(true);
    dialog.show();
    if (!dialog.isOK()) return;
    Configuration newConfiguration = dialog.getConfiguration();
    copyConfiguration(newConfiguration, configuration);
    configurationsChanged(dialog.getSearchContext());
  }

  private class MyListModel extends AbstractListModel {
    public int getSize() {
      return myConfigurations.size();
    }

    public Object getElementAt(int index) {
      return index < myConfigurations.size() ? myConfigurations.get(index) : null;
    }

    public void fireContentsChanged() {
      fireContentsChanged(myTemplatesList, -1, -1);
    }
  }
}
