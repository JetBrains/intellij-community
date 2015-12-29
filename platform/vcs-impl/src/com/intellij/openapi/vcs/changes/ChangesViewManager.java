/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.actions.IgnoredSettingsAction;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ChangesViewManager implements ChangesViewI, JDOMExternalizable, ProjectComponent {
  private static final int UNVERSIONED_MAX_SIZE = 50;
  private boolean SHOW_FLATTEN_MODE = true;
  private boolean SHOW_IGNORED_MODE = false;

  private final ChangesListView myView;
  private JPanel myProgressLabel;

  private final Alarm myRepaintAlarm;

  private boolean myDisposed = false;

  private final ChangeListListener myListener = new MyChangeListListener();
  private final Project myProject;
  private final ChangesViewContentManager myContentManager;

  @NonNls private static final String ATT_FLATTENED_VIEW = "flattened_view";
  @NonNls private static final String ATT_SHOW_IGNORED = "show_ignored";
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangesViewManager");
  private Splitter mySplitter;
  private MessageBusConnection myConnection;
  private ChangesViewManager.ToggleDetailsAction myToggleDetailsAction;

  private boolean myDetailsOn;
  private final MyChangeProcessor myDiffDetails;

  private final TreeSelectionListener myTsl;
  private Content myContent;
  private static final String DETAILS_SPLITTER_PROPORTION = "ChangesViewManager.DETAILS_SPLITTER_PROPORTION";

  public static ChangesViewI getInstance(Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetComponent(project, ChangesViewI.class);
  }

  public ChangesViewManager(Project project, ChangesViewContentManager contentManager, final VcsChangeDetailsManager vcsChangeDetailsManager) {
    myProject = project;
    myContentManager = contentManager;
    myView = new ChangesListView(project);

    Disposer.register(project, myView);
    myRepaintAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
    myDiffDetails = new MyChangeProcessor(myProject);
    myTsl = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (LOG.isDebugEnabled()) {
          String message = "selection changed. selected:  " + toStringPaths(myView.getSelectionPaths());
          if (LOG.isTraceEnabled()) {
            LOG.trace(message + " from: " + DebugUtil.currentStackTrace());
          }
          else {
            LOG.debug(message);
          }
        }
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            changeDetails();
          }
        });
      }

      private String toStringPaths(TreePath[] paths) {
        if (paths == null) return "null";
        if (paths.length == 0) return "empty";
        final StringBuilder sb = new StringBuilder();
        for (TreePath path : paths) {
          if (sb.length() > 0) {
            sb.append(", ");
          }
          sb.append(path.toString());
        }
        return sb.toString();
      }
    };
  }

  public void projectOpened() {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    changeListManager.addChangeListListener(myListener);
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        changeListManager.removeChangeListListener(myListener);
      }
    });
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    myContent = ContentFactory.SERVICE.getInstance().createContent(createChangeViewComponent(), ChangesViewContentManager.LOCAL_CHANGES, false);
    myContent.setCloseable(false);
    myContentManager.addContent(myContent);

    scheduleRefresh();
    myConnection = myProject.getMessageBus().connect(myProject);
    myConnection.subscribe(RemoteRevisionsCache.REMOTE_VERSION_CHANGED, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            refreshView();
          }
        }, ModalityState.NON_MODAL, myProject.getDisposed());
      }
    });
    // not sure we should turn it on on start (and pre-select smthg - rather heavy actions..)
    /*if (VcsConfiguration.getInstance(myProject).CHANGE_DETAILS_ON) {
      myToggleDetailsAction.actionPerformed(null);
    }*/
  }

  public void projectClosed() {
    PropertiesComponent.getInstance().setValue(DETAILS_SPLITTER_PROPORTION, String.valueOf(mySplitter.getProportion()));
    Disposer.dispose(myDiffDetails);
    myView.removeTreeSelectionListener(myTsl);
    myConnection.disconnect();
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

    EmptyAction.registerWithShortcutSet("ChangesView.Refresh", CommonShortcuts.getRerun(), panel);
    EmptyAction.registerWithShortcutSet("ChangesView.NewChangeList", CommonShortcuts.getNew(), panel);
    EmptyAction.registerWithShortcutSet("ChangesView.RemoveChangeList", CommonShortcuts.getDelete(), panel);
    EmptyAction.registerWithShortcutSet(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST, CommonShortcuts.getMove(), panel);
    EmptyAction.registerWithShortcutSet("ChangesView.Rename",CommonShortcuts.getRename() , panel);
    EmptyAction.registerWithShortcutSet("ChangesView.SetDefault", new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.ALT_DOWN_MASK | ctrlMask())), panel);
    EmptyAction.registerWithShortcutSet("ChangesView.Diff", CommonShortcuts.getDiff(), panel);

    JPanel toolbarPanel = new JPanel(new BorderLayout());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CHANGES_VIEW_TOOLBAR, group, false);
    toolbar.setTargetComponent(myView);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarPanel.add(toolbarComponent, BorderLayout.WEST);

    DefaultActionGroup visualActionsGroup = new DefaultActionGroup();
    final Expander expander = new Expander();
    visualActionsGroup.add(CommonActionsManager.getInstance().createExpandAllAction(expander, panel));
    visualActionsGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(expander, panel));

    ToggleShowFlattenAction showFlattenAction = new ToggleShowFlattenAction();
    showFlattenAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P, ctrlMask())), panel);
    visualActionsGroup.add(showFlattenAction);
    visualActionsGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));
    visualActionsGroup.add(new ToggleShowIgnoredAction());
    visualActionsGroup.add(new IgnoredSettingsAction());
    myToggleDetailsAction = new ToggleDetailsAction();
    visualActionsGroup.add(myToggleDetailsAction);
    visualActionsGroup.add(new ContextHelpAction(ChangesListView.ourHelpId));
    toolbarPanel.add(
      ActionManager.getInstance().createActionToolbar(ActionPlaces.CHANGES_VIEW_TOOLBAR, visualActionsGroup, false).getComponent(), BorderLayout.CENTER);


    DefaultActionGroup menuGroup = (DefaultActionGroup) ActionManager.getInstance().getAction("ChangesViewPopupMenu");
    myView.setMenuActions(menuGroup);

    myView.setShowFlatten(SHOW_FLATTEN_MODE);

    myProgressLabel = new JPanel(new BorderLayout());

    panel.setToolbar(toolbarPanel);

    final JPanel content = new JPanel(new BorderLayout());
    String value = PropertiesComponent.getInstance().getValue(DETAILS_SPLITTER_PROPORTION);
    float f = 0.5f;
    if (! StringUtil.isEmptyOrSpaces(value)) {
      try {
        f = Float.parseFloat(value);
      } catch (NumberFormatException e) {
        //
      }
    }
    mySplitter = new Splitter(false, f);
    mySplitter.setHonorComponentsMinimumSize(false);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myView);
    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(scrollPane, BorderLayout.CENTER);
    mySplitter.setFirstComponent(wrapper);
    content.add(mySplitter, BorderLayout.CENTER);
    content.add(myProgressLabel, BorderLayout.SOUTH);
    panel.setContent(content);

    myView.installDndSupport(ChangeListManagerImpl.getInstanceImpl(myProject));
    myView.addTreeSelectionListener(myTsl);
    return panel;
  }

  private void changeDetails() {
    if (!myDetailsOn) {
      myDiffDetails.clear();

      if (mySplitter.getSecondComponent() != null) {
        setChangeDetailsPanel(null);
      }
    }
    else {
      myDiffDetails.refresh();

      if (mySplitter.getSecondComponent() == null) {
        setChangeDetailsPanel(myDiffDetails.getComponent());
      }
    }
  }

  private void setChangeDetailsPanel(@Nullable final JComponent component) {
    mySplitter.setSecondComponent(component);
    mySplitter.getFirstComponent().setBorder(component == null ? null : IdeBorderFactory.createBorder(SideBorder.RIGHT));
    mySplitter.revalidate();
    mySplitter.repaint();
  }

  @JdkConstants.InputEventMask
  private static int ctrlMask() {
    return SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
  }

  private void updateProgressComponent(final Factory<JComponent> progress) {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (myProgressLabel != null) {
          myProgressLabel.removeAll();
          myProgressLabel.add(progress.create());
          myProgressLabel.setMinimumSize(JBUI.emptySize());
        }
      }
    });
  }
  public void updateProgressText(final String text, final boolean isError) {
    updateProgressComponent(createTextStatusFactory(text, isError));
  }

  @Override
  public void setBusy(final boolean b) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        myView.setPaintBusy(b);
      }
    });
  }

  public static Factory<JComponent> createTextStatusFactory(final String text, final boolean isError) {
    return new Factory<JComponent>() {
      @Override
      public JComponent create() {
        JLabel label = new JLabel(text);
        label.setForeground(isError ? JBColor.RED : UIUtil.getLabelForeground());
        return label;
      }
    };
  }

  @Override
  public void scheduleRefresh() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    if (myProject == null || myProject.isDisposed()) { return; }
    int was = myRepaintAlarm.cancelAllRequests();
    if (LOG.isDebugEnabled()) {
      LOG.debug("schedule refresh, was " + was);
    }
    if (!myRepaintAlarm.isDisposed()) {
      myRepaintAlarm.addRequest(new Runnable() {
        public void run() {
          refreshView();
        }
      }, 100, ModalityState.NON_MODAL);
    }
  }

  private void refreshView() {
    if (myDisposed || ! myProject.isInitialized() || ApplicationManager.getApplication().isUnitTestMode()) return;
    if (! ProjectLevelVcsManager.getInstance(myProject).hasActiveVcss()) return;

    ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);

    final Couple<Integer> unv = changeListManager.getUnversionedFilesSize();
    final boolean manyUnversioned = unv.getFirst() > UNVERSIONED_MAX_SIZE;
    final Trinity<List<VirtualFile>, Integer, Integer> unversionedPair =
      new Trinity<List<VirtualFile>, Integer, Integer>(manyUnversioned ? Collections.<VirtualFile>emptyList() : changeListManager.getUnversionedFiles(), unv.getFirst(),
                                                       unv.getSecond());

    if (LOG.isDebugEnabled()) {
      LOG.debug("refresh view, unversioned collections size: " + unversionedPair.getFirst().size() + " unv size passed: " +
                unversionedPair.getSecond() + " dirs: " + unversionedPair.getThird());
    }
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

  @Override
  public void setShowFlattenMode(boolean state) {
    SHOW_FLATTEN_MODE = state;
    myView.setShowFlatten(SHOW_FLATTEN_MODE);
    refreshView();
  }

  @Override
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

  @Override
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
      setBusy(false);
      if (updateException == null) {
        updateProgressText("", false);
        final Factory<JComponent> additionalUpdateInfo = changeListManager.getAdditionalUpdateInfo();
        if (additionalUpdateInfo != null) {
          updateProgressComponent(additionalUpdateInfo);
        }
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

  private class ToggleShowFlattenAction extends ToggleAction implements DumbAware {
    public ToggleShowFlattenAction() {
      super(VcsBundle.message("changes.action.show.directories.text"),
            VcsBundle.message("changes.action.show.directories.description"),
            AllIcons.Actions.GroupByPackage);
    }

    public boolean isSelected(AnActionEvent e) {
      return !SHOW_FLATTEN_MODE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      setShowFlattenMode(!state);
    }
  }

  private class ToggleShowIgnoredAction extends ToggleAction implements DumbAware {
    public ToggleShowIgnoredAction() {
      super(VcsBundle.message("changes.action.show.ignored.text"),
            VcsBundle.message("changes.action.show.ignored.description"),
            AllIcons.Actions.ShowHiddens);
    }

    public boolean isSelected(AnActionEvent e) {
      return SHOW_IGNORED_MODE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      SHOW_IGNORED_MODE = state;
      refreshView();
    }
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void initComponent() {
  }

  private class ToggleDetailsAction extends ToggleAction implements DumbAware {
    private ToggleDetailsAction() {
      super("Preview Diff", null, AllIcons.Actions.PreviewDetails);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myDetailsOn;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myDetailsOn = state;
      // not sure we should turn it on on start (and pre-select smthg - rather heavy actions..)
      //      VcsConfiguration.getInstance(myProject).CHANGE_DETAILS_ON = myDetailsOn;
      changeDetails();
    }
  }

  private class MyChangeProcessor extends CacheChangeProcessor {
    public MyChangeProcessor(@NotNull Project project) {
      super(project, DiffPlaces.CHANGES_VIEW);
    }

    @Override
    public boolean isWindowFocused() {
      return DiffUtil.isFocusedComponent(myProject, myContent.getComponent());
    }

    @NotNull
    @Override
    protected List<Change> getSelectedChanges() {
      return Arrays.asList(myView.getSelectedChanges());
    }

    @NotNull
    @Override
    protected List<Change> getAllChanges() {
      return Arrays.asList(myView.getChanges());
    }

    @Override
    protected void selectChange(@NotNull Change change) {
      DefaultMutableTreeNode root = (DefaultMutableTreeNode)myView.getModel().getRoot();
      DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, change);
      if (node != null) {
        TreePath path = TreeUtil.getPathFromRoot(node);
        TreeUtil.selectPath(myView, path, false);
      }
    }
  }
}
