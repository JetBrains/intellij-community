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
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.actions.MoveChangesToAnotherListAction;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class UnversionedViewDialog extends DialogWrapper {
  private JPanel myPanel;
  private final ChangesListView myView;
  private final ChangeListManager myChangeListManager;
  private boolean myInRefresh;
  private final Project myProject;
  private boolean myFlattenState;

  public UnversionedViewDialog(final Project project) {
    super(project, true);
    setTitle("Unversioned files");
    myProject = project;
    myView = new ChangesListView(project) {
      @Override
      public void calcData(DataKey key, DataSink sink) {
        super.calcData(key, sink);
        if (ChangesListView.UNVERSIONED_FILES_DATA_KEY.is(key.getName())) {
          sink.put(key, Arrays.asList(getSelectedFiles()));
        }
      }
    };
    myChangeListManager = ChangeListManager.getInstance(project);
    createPanel();
    setOKButtonText("Close");

    init();
    initData(((ChangeListManagerImpl) myChangeListManager).getUnversionedFiles());
    myView.setMinimumSize(new Dimension(100, 100));
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  private void initData(final List<VirtualFile> files) {
    final TreeState state = TreeState.createOn(myView, (ChangesBrowserNode)myView.getModel().getRoot());

    TreeModelBuilder builder = new TreeModelBuilder(myProject, myFlattenState);
    final DefaultTreeModel model = builder.buildModelFromFiles(files);
    myView.setModel(model);
    myView.setCellRenderer(new ChangesBrowserNodeRenderer(myProject, myFlattenState, true));
    myView.expandPath(new TreePath(((ChangesBrowserNode)model.getRoot()).getPath()));

    state.applyTo(myView);
  }

  private void createPanel() {
    myPanel = new JPanel(new BorderLayout());

    final DefaultActionGroup group = new DefaultActionGroup();
    final CommonActionsManager cam = CommonActionsManager.getInstance();
    final Expander expander = new Expander();
    final AnAction expandAction = cam.createExpandAllAction(expander, myView);
    group.add(expandAction);
    final AnAction collapseAction = cam.createCollapseAllAction(expander, myView);
    group.add(collapseAction);
    group.add(new ToggleShowFlattenAction());
    group.add(new MoveChangesToAnotherListAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        super.actionPerformed(e);
        refreshView();
      }
    });

    myView.setMenuActions(group);
    myView.setShowFlatten(false);

    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("UNVERSIONED_DIALOG", group, false);
    myPanel.add(actionToolbar.getComponent(), BorderLayout.WEST);
    myPanel.add(new JScrollPane(myView), BorderLayout.CENTER);
  }

  @Override
  protected String getDimensionServiceKey() {
    return "com.intellij.openapi.vcs.changes.UnversionedViewDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myView;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private class Expander implements TreeExpander {
    public void expandAll() {
      TreeUtil.expandAll(myView);
    }

    public boolean canExpand() {
      return true;
    }

    public void collapseAll() {
      TreeUtil.collapseAll(myView, 1);
      TreeUtil.expand(myView, 0);
    }

    public boolean canCollapse() {
      return true;
    }
  }

  private void refreshView() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myInRefresh) return;
    myInRefresh = true;
    
    myChangeListManager.invokeAfterUpdate(new Runnable() {
      public void run() {
        try {
          initData(((ChangeListManagerImpl) myChangeListManager).getUnversionedFiles());
        } finally {
          myInRefresh = false;
        }
      }
    }, InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE, "", ModalityState.current());
  }

  public class ToggleShowFlattenAction extends ToggleAction implements DumbAware {
    public ToggleShowFlattenAction() {
      super(VcsBundle.message("changes.action.show.directories.text"),
            VcsBundle.message("changes.action.show.directories.description"),
            Icons.DIRECTORY_CLOSED_ICON);
      myFlattenState = false;
    }

    public boolean isSelected(AnActionEvent e) {
      return !myFlattenState;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myFlattenState = !state;
      myView.setShowFlatten(myFlattenState);
      refreshView();
    }
  }
}
