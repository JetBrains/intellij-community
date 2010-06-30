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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 11.07.2006
 * Time: 15:29:25
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.actions.IgnoredSettingsAction;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.Alarm;
import com.intellij.util.Icons;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ChangesViewManager extends AbstractProjectComponent implements JDOMExternalizable {
  public static final int UNVERSIONED_MAX_SIZE = 50;
  private boolean SHOW_FLATTEN_MODE = true;
  private boolean SHOW_IGNORED_MODE = false;

  private final ChangesListView myView;
  private JLabel myProgressLabel;

  private final Alarm myRepaintAlarm;

  private boolean myDisposed = false;

  private final ChangeListListener myListener = new MyChangeListListener();
  private final ChangesViewContentManager myContentManager;

  @NonNls private static final String ATT_FLATTENED_VIEW = "flattened_view";
  @NonNls private static final String ATT_SHOW_IGNORED = "show_ignored";

  public static ChangesViewManager getInstance(Project project) {
    return project.getComponent(ChangesViewManager.class);
  }

  public static ChangesViewManager getInstanceChecked(final Project project) {
    return ApplicationManager.getApplication().runReadAction(new Computable<ChangesViewManager>() {
      public ChangesViewManager compute() {
        if (project.isDisposed()) throw new ProcessCanceledException();
        return project.getComponent(ChangesViewManager.class);
      }
    });
  }

  public ChangesViewManager(Project project, ChangesViewContentManager contentManager) {
    super(project);
    myContentManager = contentManager;
    myView = new ChangesListView(project);
    Disposer.register(project, myView);
    myRepaintAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
  }

  public void projectOpened() {
    ChangeListManager.getInstance(myProject).addChangeListListener(myListener);
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        ChangeListManager.getInstance(myProject).removeChangeListListener(myListener);
      }
    });
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    final Content content = ContentFactory.SERVICE.getInstance().createContent(createChangeViewComponent(), "Local", false);
    content.setCloseable(false);
    myContentManager.addContent(content);

    scheduleRefresh();
    final MessageBusConnection connection = myProject.getMessageBus().connect(myProject);
    connection.subscribe(RemoteRevisionsCache.REMOTE_VERSION_CHANGED, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            refreshView();
          }
        }, ModalityState.NON_MODAL, myProject.getDisposed());
      }
    });
  }

  public void projectClosed() {
    myDisposed = true;
    myRepaintAlarm.cancelAllRequests();
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "ChangesViewManager";
  }

  private JComponent createChangeViewComponent() {
    SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);

    DefaultActionGroup group = (DefaultActionGroup) ActionManager.getInstance().getAction("ChangesViewToolbar");

    ActionManager.getInstance().getAction("ChangesView.Refresh").registerCustomShortcutSet(CommonShortcuts.getRerun(), panel);
    ActionManager.getInstance().getAction("ChangesView.NewChangeList").registerCustomShortcutSet(CommonShortcuts.getNew(), panel);
    ActionManager.getInstance().getAction("ChangesView.RemoveChangeList").registerCustomShortcutSet(CommonShortcuts.DELETE, panel);
    ActionManager.getInstance().getAction("ChangesView.Move").registerCustomShortcutSet(CommonShortcuts.getMove(), panel);
    ActionManager.getInstance().getAction("ChangesView.Rename").registerCustomShortcutSet(CommonShortcuts.getRename(), panel);
    ActionManager.getInstance().getAction("ChangesView.SetDefault").registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_U, KeyEvent.ALT_DOWN_MASK | ctrlMask())), panel);

    final CustomShortcutSet diffShortcut =
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, ctrlMask()));
    ActionManager.getInstance().getAction("ChangesView.Diff").registerCustomShortcutSet(diffShortcut, panel);

    JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.add(createToolbarComponent(group), BorderLayout.WEST);

    DefaultActionGroup visualActionsGroup = new DefaultActionGroup();
    final Expander expander = new Expander();
    visualActionsGroup.add(CommonActionsManager.getInstance().createExpandAllAction(expander, panel));
    visualActionsGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(expander, panel));

    ToggleShowFlattenAction showFlattenAction = new ToggleShowFlattenAction();
    showFlattenAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P,
                                                                                             ctrlMask())),
                                                panel);
    visualActionsGroup.add(showFlattenAction);
    visualActionsGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));                                              
    visualActionsGroup.add(new ToggleShowIgnoredAction());
    visualActionsGroup.add(new IgnoredSettingsAction());
    visualActionsGroup.add(new ContextHelpAction(ChangesListView.ourHelpId));
    toolbarPanel.add(createToolbarComponent(visualActionsGroup), BorderLayout.CENTER);


    DefaultActionGroup menuGroup = (DefaultActionGroup) ActionManager.getInstance().getAction("ChangesViewPopupMenu");
    myView.setMenuActions(menuGroup);

    myView.setShowFlatten(SHOW_FLATTEN_MODE);

    myProgressLabel = new JLabel();

    panel.setToolbar(toolbarPanel);

    final JPanel content = new JPanel(new BorderLayout());
    content.add(ScrollPaneFactory.createScrollPane(myView), BorderLayout.CENTER);
    content.add(myProgressLabel, BorderLayout.SOUTH);
    panel.setContent(content);

    myView.installDndSupport(ChangeListManagerImpl.getInstanceImpl(myProject));
    return panel;
  }

  private int ctrlMask() {
    return SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
  }

  private static JComponent createToolbarComponent(final DefaultActionGroup group) {
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CHANGES_VIEW_TOOLBAR, group, false);
    return actionToolbar.getComponent();
  }

  void updateProgressText(final String text, final boolean isError) {
    if (myProgressLabel != null) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myProgressLabel.setText(text);
          myProgressLabel.setForeground(isError ? Color.red : UIUtil.getLabelForeground());
        }
      });
    }
  }

  public void scheduleRefresh() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    myRepaintAlarm.cancelAllRequests();
    myRepaintAlarm.addRequest(new Runnable() {
      public void run() {
        refreshView();
      }
    }, 100, ModalityState.NON_MODAL);
  }

  void refreshView() {
    if (myDisposed || ! myProject.isInitialized() || ApplicationManager.getApplication().isUnitTestMode()) return;
    ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);

    final Pair<Integer, Integer> unv = changeListManager.getUnversionedFilesSize();
    final boolean manyUnversioned = unv.getFirst() > UNVERSIONED_MAX_SIZE;
    final Trinity<List<VirtualFile>, Integer, Integer> unversionedPair =
      new Trinity<List<VirtualFile>, Integer, Integer>(manyUnversioned ? Collections.<VirtualFile>emptyList() : changeListManager.getUnversionedFiles(), unv.getFirst(),
                                                       unv.getSecond());

    myView.updateModel(changeListManager.getChangeListsCopy(), unversionedPair,
                       changeListManager.getDeletedFiles(),
                       changeListManager.getModifiedWithoutEditing(),
                       changeListManager.getSwitchedFilesMap(),
                       changeListManager.getSwitchedRoots(),
                       SHOW_IGNORED_MODE ? changeListManager.getIgnoredFiles() : null, changeListManager.getLockedFolders(),
                       changeListManager.getLogicallyLockedFolders());
  }

  public void readExternal(Element element) throws InvalidDataException {
    SHOW_FLATTEN_MODE = Boolean.valueOf(element.getAttributeValue(ATT_FLATTENED_VIEW)).booleanValue();
    SHOW_IGNORED_MODE = Boolean.valueOf(element.getAttributeValue(ATT_SHOW_IGNORED)).booleanValue();
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(ATT_FLATTENED_VIEW, String.valueOf(SHOW_FLATTEN_MODE));
    element.setAttribute(ATT_SHOW_IGNORED, String.valueOf(SHOW_IGNORED_MODE));
  }

  public void selectFile(final VirtualFile vFile) {
    if (vFile == null) return;
    Change change = ChangeListManager.getInstance(myProject).getChange(vFile);
    Object objectToFind = change != null ? change : vFile;

    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myView.getModel().getRoot();
    DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, objectToFind);
    if (node != null) {
      TreeUtil.selectNode(myView, node);
    }
  }

  public void refreshChangesViewNodeAsync(final VirtualFile file) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        refreshChangesViewNode(file);
      }
    });
  }

  private void refreshChangesViewNode(final VirtualFile file) {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myView.getModel().getRoot();
    Object userObject;
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    if (changeListManager.isUnversioned(file)) {
      userObject = file;
    }
    else {
      userObject = changeListManager.getChange(file);
    }
    if (userObject != null) {
      final DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, userObject);
      if (node != null) {
        myView.getModel().nodeChanged(node);
      }
    }
  }

  private class MyChangeListListener extends ChangeListAdapter {

    public void changeListAdded(ChangeList list) {
      scheduleRefresh();
    }

    public void changeListRemoved(ChangeList list) {
      scheduleRefresh();
    }

    public void changeListRenamed(ChangeList list, String oldName) {
      scheduleRefresh();
    }

    public void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList) {
      scheduleRefresh();
    }

    public void defaultListChanged(final ChangeList oldDefaultList, ChangeList newDefaultList) {
      scheduleRefresh();
    }

    public void changeListUpdateDone() {
      scheduleRefresh();
      ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
      VcsException updateException = changeListManager.getUpdateException();
      if (updateException == null) {
        updateProgressText("", false);
      }
      else {
        updateProgressText(VcsBundle.message("error.updating.changes", updateException.getMessage()), true);
      }
    }
  }

  private class Expander implements TreeExpander {
    public void expandAll() {
      TreeUtil.expandAll(myView);
    }

    public boolean canExpand() {
      return true;
    }

    public void collapseAll() {
      TreeUtil.collapseAll(myView, 2);
      TreeUtil.expand(myView, 1);
    }

    public boolean canCollapse() {
      return true;
    }
  }

  public class ToggleShowFlattenAction extends ToggleAction implements DumbAware {
    public ToggleShowFlattenAction() {
      super(VcsBundle.message("changes.action.show.directories.text"),
            VcsBundle.message("changes.action.show.directories.description"),
            Icons.DIRECTORY_CLOSED_ICON);
    }

    public boolean isSelected(AnActionEvent e) {
      return !SHOW_FLATTEN_MODE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      SHOW_FLATTEN_MODE = !state;
      myView.setShowFlatten(SHOW_FLATTEN_MODE);
      refreshView();
    }
  }

  public class ToggleShowIgnoredAction extends ToggleAction implements DumbAware {
    public ToggleShowIgnoredAction() {
      super(VcsBundle.message("changes.action.show.ignored.text"),
            VcsBundle.message("changes.action.show.ignored.description"),
            IconLoader.getIcon("/actions/showHiddens.png"));
    }

    public boolean isSelected(AnActionEvent e) {
      return SHOW_IGNORED_MODE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      SHOW_IGNORED_MODE = state;
      refreshView();
    }
  }
}
