/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.committed.CommittedChangeListRenderer;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.util.NullableConsumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.*;
import java.util.List;

/**
 * @author yole
 */
public class ChangeListChooserPanel extends JPanel {
  private static final Comparator<ChangeList> CHANGE_LIST_COMPARATOR = new Comparator<ChangeList>() {
    @Override
    public int compare(ChangeList o1, ChangeList o2) {
      return o1.getName().compareToIgnoreCase(o2.getName());
    }
  };

  private JPanel myPanel;
  private JRadioButton myRbExisting;
  private JRadioButton myRbNew;
  private JComboBox myExistingListsCombo;
  private NewEditChangelistPanel myNewListPanel;
  private final NullableConsumer<String> myOkEnabledListener;
  private Project myProject;

  public ChangeListChooserPanel(final Project project, @NotNull final NullableConsumer<String> okEnabledListener) {
    super(new BorderLayout());
    myProject = project;
    myOkEnabledListener = okEnabledListener;
    add(myPanel, BorderLayout.CENTER);

    myRbExisting.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateEnabledItems();
      }
    });
  }

  public void init() {
    myExistingListsCombo.setRenderer(new ColoredListCellRendererWrapper() {
      private final IssueLinkRenderer myLinkRenderer = new IssueLinkRenderer(myProject, this);

      @Override
      protected void doCustomize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof LocalChangeList) {
          String name = ((LocalChangeList) value).getName();

          if (myExistingListsCombo.getWidth() == 0) {
            name = name.length() > 10 ? name.substring(0, 7) + " .." : name;
          }
          else {
            final FontMetrics fm = list.getFontMetrics(list.getFont());
            final int width = fm.stringWidth(name);
            final int listWidth = myExistingListsCombo.getWidth();
            if ((listWidth > 0) && (width > listWidth)) {
              final String truncated = CommittedChangeListRenderer.truncateDescription(name, fm, listWidth - fm.stringWidth(" ..") - 7);
              if (truncated.length() > 5) {
                name = truncated + " ..";
              }
            }
          }
          myLinkRenderer.appendTextWithLinks(name, ((LocalChangeList)value).isDefault()
                                                   ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });
    myNewListPanel.init(null);
    myRbNew.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myRbNew.isSelected()) {
          IdeFocusManager.getInstance(myProject).requestFocus(myNewListPanel.getPreferredFocusedComponent(), true);
        }
      }
    });
    final ComboboxSpeedSearch search = new ComboboxSpeedSearch(myExistingListsCombo);
    search.setComparator(new SpeedSearchComparator(true, false));
  }

  public void setChangeLists(Collection<? extends ChangeList> changeLists) {
    List<ChangeList> list = new ArrayList<ChangeList>(changeLists);
    Collections.sort(list, CHANGE_LIST_COMPARATOR);
    myExistingListsCombo.setModel(new CollectionComboBoxModel(list, null));
  }

  public void setDefaultName(String name) {
    if (! StringUtil.isEmptyOrSpaces(name)) {
      myNewListPanel.setChangeListName(name);
    }
  }

  private void updateEnabledItems() {
    if (myRbExisting.isSelected()) {
      myExistingListsCombo.setEnabled(true);
      UIUtil.setEnabled(myNewListPanel, false, true);
      myExistingListsCombo.requestFocus();
    }
    else {
      myExistingListsCombo.setEnabled(false);
      UIUtil.setEnabled(myNewListPanel, true, true);
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
      String newText = myNewListPanel.getChangeListName();
      if (manager.findChangeList(newText) != null) {
        Messages.showErrorDialog(project,
                                 VcsBundle.message("changes.newchangelist.warning.already.exists.text", newText),
                                 VcsBundle.message("changes.newchangelist.warning.already.exists.title"));
        return null;
      }
    }
    final boolean existingSelected = myRbExisting.isSelected();
    VcsConfiguration.getInstance(myProject).PRESELECT_EXISTING_CHANGELIST = existingSelected;

    if (existingSelected) {
      return (LocalChangeList)myExistingListsCombo.getSelectedItem();
    }
    else {
      LocalChangeList changeList = manager.addChangeList(myNewListPanel.getChangeListName(), myNewListPanel.getDescription());
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
      myExistingListsCombo.setSelectedIndex(0);
    }
    else {
      myExistingListsCombo.setSelectedItem(defaultSelection);
    }
    //if defaultSelection was predefined as null then it means we could not use existing is this context
    if (defaultSelection != null && VcsConfiguration.getInstance(myProject).PRESELECT_EXISTING_CHANGELIST) {
      myRbExisting.setSelected(true);
    }
    else {
      myRbNew.setSelected(true);
    }

    updateEnabledItems();
  }

  public JComponent getPreferredFocusedComponent() {
    return myRbExisting.isSelected() ? myExistingListsCombo : myNewListPanel.getPreferredFocusedComponent();
  }

  private void createUIComponents() {
    myNewListPanel = new NewEditChangelistPanel(myProject) {

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
