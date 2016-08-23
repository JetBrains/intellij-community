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

import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;

public class UnversionedViewDialog extends DialogWrapper {
  private JPanel myPanel;
  private final ChangesListView myView;
  private final ChangeListManager myChangeListManager;
  private boolean myInRefresh;
  private final Project myProject;
  private AnAction myDeleteActionWithCustomShortcut;

  public UnversionedViewDialog(@NotNull Project project) {
    super(project, true);
    setTitle("Unversioned Files");
    myProject = project;
    final Runnable closer = new Runnable() {
      public void run() {
        UnversionedViewDialog.this.close(0);
      }
    };
    myView = new ChangesListView(project) {
      @Override
      public void calcData(DataKey key, DataSink sink) {
        super.calcData(key, sink);
        if (ChangesListView.UNVERSIONED_FILES_DATA_KEY.is(key.getName())) {
          sink.put(ChangesListView.UNVERSIONED_FILES_DATA_KEY, getSelectedFiles());
        }
      }

      @Override
      protected void editSourceRegistration() {
        EditSourceOnDoubleClickHandler.install(this, closer);
        EditSourceOnEnterKeyHandler.install(this, closer);
      }
    };
    myChangeListManager = ChangeListManager.getInstance(project);
    createPanel();
    setOKButtonText("Close");

    init();
    initData(((ChangeListManagerImpl) myChangeListManager).getUnversionedFiles());
    myView.setMinimumSize(new Dimension(100, 100));
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  private void initData(final List<VirtualFile> files) {
    final TreeState state = TreeState.createOn(myView, (ChangesBrowserNode)myView.getModel().getRoot());

    TreeModelBuilder builder = new TreeModelBuilder(myProject, myView.isShowFlatten());
    final DefaultTreeModel model = builder.buildModelFromFiles(files);
    myView.setModel(model);
    myView.expandPath(new TreePath(((ChangesBrowserNode)model.getRoot()).getPath()));

    state.applyTo(myView);
  }

  private void createPanel() {
    myPanel = new JPanel(new BorderLayout());

    final DefaultActionGroup group = new DefaultActionGroup();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("UNVERSIONED_DIALOG", group, true);

    List<AnAction> actions = registerUnversionedActionsShortcuts(actionToolbar.getToolbarDataContext(), myView);
    // special shortcut for deleting a file
    actions.add(myDeleteActionWithCustomShortcut =
                  EmptyAction.registerWithShortcutSet("ChangesView.DeleteUnversioned.From.Dialog", CommonShortcuts.getDelete(), myView));

    refreshViewAfterActionPerformed(actions);
    group.add(getUnversionedActionGroup());

    final CommonActionsManager cam = CommonActionsManager.getInstance();
    final Expander expander = new Expander();
    group.addSeparator();
    group.add(new ToggleShowFlattenAction());
    group.add(cam.createExpandAllAction(expander, myView));
    group.add(cam.createCollapseAllAction(expander, myView));

    myPanel.add(actionToolbar.getComponent(), BorderLayout.NORTH);
    myPanel.add(ScrollPaneFactory.createScrollPane(myView), BorderLayout.CENTER);

    final DefaultActionGroup secondGroup = new DefaultActionGroup();
    secondGroup.addAll(getUnversionedActionGroup());

    myView.setMenuActions(secondGroup);
    myView.setShowFlatten(false);
  }

  private void refreshViewAfterActionPerformed(@NotNull final List<AnAction> actions) {
    ActionManager.getInstance().addAnActionListener(new AnActionListener.Adapter() {
      @Override
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (actions.contains(action)) {
          refreshView();
          if (myDeleteActionWithCustomShortcut.equals(action)) {
            // We can not utilize passed "dataContext" here as it results in
            // "cannot share data context between Swing events" assertion.
            refreshChanges(myProject, ChangesBrowserBase.DATA_KEY.getData(DataManager.getInstance().getDataContext(myView)));
          }
        }
      }
    }, myDisposable);
  }

  @NotNull
  public static ActionGroup getUnversionedActionGroup() {
    return (ActionGroup)ActionManager.getInstance().getAction("Unversioned.Files.Dialog");
  }

  @NotNull
  public static List<AnAction> registerUnversionedActionsShortcuts(@NotNull DataContext dataContext, @NotNull JComponent component) {
    ActionManager manager = ActionManager.getInstance();
    List<AnAction> actions = ContainerUtil.newArrayList();

    Utils.expandActionGroup(getUnversionedActionGroup(), actions, new PresentationFactory(), dataContext, "", manager);
    for (AnAction action : actions) {
      action.registerCustomShortcutSet(action.getShortcutSet(), component);
    }

    return actions;
  }

  public static void refreshChanges(@NotNull Project project, @Nullable ChangesBrowserBase browser) {
    if (browser != null) {
      ChangeListManager.getInstance(project)
        .invokeAfterUpdate(browser::rebuildList, InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE, "Delete files", null);
    }
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
      return !myView.isShowFlatten();
    }

    public void collapseAll() {
      TreeUtil.collapseAll(myView, 1);
      TreeUtil.expand(myView, 0);
    }

    public boolean canCollapse() {
      return !myView.isShowFlatten();
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
            AllIcons.Actions.GroupByPackage);
    }

    public boolean isSelected(AnActionEvent e) {
      return !myView.isShowFlatten();
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myView.setShowFlatten(!state);
      refreshView();
    }
  }
}
