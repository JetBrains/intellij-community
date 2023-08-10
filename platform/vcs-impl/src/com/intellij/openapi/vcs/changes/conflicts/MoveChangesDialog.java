// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class MoveChangesDialog extends DialogWrapper {
  private static final String MOVE_CHANGES_CURRENT_ONLY = "move.changes.current.only";
  private final AsyncChangesTree myTreeList;
  private final Collection<? extends Change> mySelected;
  private final JBCheckBox myCheckBox;

  public MoveChangesDialog(final Project project,
                           Collection<? extends Change> selected,
                           final Set<ChangeList> changeLists,
                           VirtualFile current) {
    super(project, true);
    mySelected = selected;
    setTitle(VcsBundle.message("dialog.title.move.changes.to.active.changelist"));
    myTreeList = new AsyncChangesTree(project, true, false) {
      @NotNull
      @Override
      protected AsyncChangesTreeModel getChangesTreeModel() {
        return SimpleAsyncChangesTreeModel.create(grouping -> {
          return TreeModelBuilder.buildFromChangeLists(project, grouping, changeLists);
        });
      }
    };
    myTreeList.requestRefresh(() -> {
      myTreeList.selectFile(current);
    });

    myCheckBox = new JBCheckBox(VcsBundle.message("checkbox.select.current.file.only"));
    myCheckBox.addActionListener(e -> setSelected(myCheckBox.isSelected()));

    boolean selectCurrent = PropertiesComponent.getInstance().getBoolean(MOVE_CHANGES_CURRENT_ONLY);
    myCheckBox.setSelected(selectCurrent);
    setSelected(selectCurrent);

    init();
  }

  private void setSelected(boolean selected) {
    myTreeList.excludeChanges(myTreeList.getIncludedSet());
    if (selected) {
      List<Change> selectedChanges = VcsTreeModelData.selected(myTreeList).userObjects(Change.class);
      myTreeList.includeChanges(selectedChanges);
    }
    else {
      myTreeList.includeChanges(mySelected);
    }
    PropertiesComponent.getInstance().setValue(MOVE_CHANGES_CURRENT_ONLY, selected);
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(ScrollPaneFactory.createScrollPane(myTreeList), BorderLayout.CENTER);

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP));
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("MoveChangesDialog", group, true);
    TreeActionsToolbarPanel toolbarPanel = new TreeActionsToolbarPanel(toolbar, myTreeList);

    panel.add(toolbarPanel, BorderLayout.NORTH);
    myTreeList.expandAll();
    myTreeList.repaint();
    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTreeList;
  }

  public Collection<Change> getIncludedChanges() {
    return VcsTreeModelData.included(myTreeList).userObjects(Change.class);
  }

  @Override
  public boolean isOKActionEnabled() {
    return !getIncludedChanges().isEmpty();
  }

  @Nullable
  @Override
  protected JComponent createDoNotAskCheckbox() {
    return myCheckBox;
  }
  /*

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    return new Action[] {
      new AbstractAction("Select &Current") {
        @Override
        public void actionPerformed(ActionEvent e) {
          ChangesBrowserNode<Change> component = (ChangesBrowserNode<Change>)myTreeList.getSelectionPath().getLastPathComponent();
          myTreeList.excludeChanges(myChanges);
        }
      },

      new AbstractAction("Select &All") {
        @Override
        public void actionPerformed(ActionEvent e) {
          myTreeList.includeChanges(myChanges);
        }
      }
    };
  }
  */
}
