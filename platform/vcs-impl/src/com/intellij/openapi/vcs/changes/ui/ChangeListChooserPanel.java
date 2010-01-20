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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListEditHandler;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collection;

/**
 * @author yole
 */
public class ChangeListChooserPanel extends JPanel {
  private JPanel myPanel;
  private JRadioButton myRbExisting;
  private JRadioButton myRbNew;
  private JComboBox myExisitingsCombo;
  private EditChangelistPanel myNewListPanel;
  @Nullable private final ChangeListEditHandler myHandler;
  private final Consumer<String> myOkEnabledListener;
  private Project myProject;

  public ChangeListChooserPanel(@Nullable final ChangeListEditHandler handler, @NotNull final Consumer<String> okEnabledListener) {
    super(new BorderLayout());
    myHandler = handler;
    myOkEnabledListener = okEnabledListener;
    add(myPanel, BorderLayout.CENTER);

    myRbExisting.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateEnabledItems();
      }
    });
  }

  public void init(final Project project) {
    myProject = project;

    myExisitingsCombo.setRenderer(new ColoredListCellRenderer() {

      private final IssueLinkRenderer myLinkRenderer = new IssueLinkRenderer(project, this);
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof LocalChangeList) {
          myLinkRenderer.appendTextWithLinks(((LocalChangeList)value).getName(),
                                             ((LocalChangeList)value).isDefault() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });
    myNewListPanel.init(project, null);
  }

  public void setChangeLists(Collection<? extends ChangeList> changeLists) {
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    for (ChangeList list : changeLists) {
      model.addElement(list);
    }

    myExisitingsCombo.setModel(model);
  }

  public void setDefaultName(String name) {
    myNewListPanel.setName(name);
  }

  private void updateEnabledItems() {
    if (myRbExisting.isSelected()) {
      myExisitingsCombo.setEnabled(true);
      myNewListPanel.setEnabled(false);
      myExisitingsCombo.requestFocus();
    }
    else {
      myExisitingsCombo.setEnabled(false);
      myNewListPanel.setEnabled(true);
      myNewListPanel.requestFocus();
    }
    if (myProject != null) {
      myNewListPanel.nameChangedImpl(myProject, null);
    }
  }

  @Nullable
  public LocalChangeList getSelectedList(Project project) {
    ChangeListManager manager = ChangeListManager.getInstance(project);
    if (myRbNew.isSelected()) {
      String newText = myNewListPanel.getName();
      if (manager.findChangeList(newText) != null) {
        Messages.showErrorDialog(project,
                                 VcsBundle.message("changes.newchangelist.warning.already.exists.text", newText),
                                 VcsBundle.message("changes.newchangelist.warning.already.exists.title"));
        return null;
      }
    }

    if (myRbExisting.isSelected()) {
      return (LocalChangeList)myExisitingsCombo.getSelectedItem();
    }
    else {
      LocalChangeList changeList = manager.addChangeList(myNewListPanel.getName(), myNewListPanel.getDescription());
      myNewListPanel.changelistCreatedOrChanged(changeList);
      if (myNewListPanel.getMakeActiveCheckBox().isSelected()) {
        manager.setDefaultChangeList(changeList);
      }
      VcsConfiguration.getInstance(project).MAKE_NEW_CHANGELIST_ACTIVE = myNewListPanel.getMakeActiveCheckBox().isSelected();

      return changeList;
    }
  }

  public void setDefaultSelection(final ChangeList defaultSelection) {
    if (defaultSelection == null) {
      myExisitingsCombo.setSelectedIndex(0);
    }
    else {
      myExisitingsCombo.setSelectedItem(defaultSelection);
    }


    if (defaultSelection != null) {
      myRbExisting.setSelected(true);
    }
    else {
      myRbNew.setSelected(true);
    }

    updateEnabledItems();
  }

  public JComponent getPreferredFocusedComponent() {
    return myRbExisting.isSelected() ? myExisitingsCombo : myNewListPanel.getPrefferedFocusedComponent();
  }

  private void createUIComponents() {
    myNewListPanel = new EditChangelistPanel(myHandler) {

      @Override
      protected void nameChanged(String errorMessage) {
        if (myRbExisting.isSelected()) {
          myOkEnabledListener.consume(null);
        } else {
          myOkEnabledListener.consume(errorMessage);
        }
      }
    };
  }
}
