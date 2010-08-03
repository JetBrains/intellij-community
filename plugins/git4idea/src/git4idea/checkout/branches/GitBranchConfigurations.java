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
package git4idea.checkout.branches;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.EventDispatcher;
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.merge.GitMergeUtil;
import git4idea.rebase.GitRebaseUtils;
import git4idea.ui.GitUIUtil;
import git4idea.vfs.GitReferenceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Git branch configurations settings and project level state
 */
@State(
  name = "Git.Branch.Configurations",
  storages = {@Storage(
    id = "ws",
    file = "$WORKSPACE_FILE$")})
public class GitBranchConfigurations implements PersistentStateComponent<GitBranchConfigurations.State>, Disposable {
  /**
   * The logger
   */
  private static final Logger LOG = Logger.getInstance(GitBranchConfigurations.class.getName());
  /**
   * The comparator for branch configuration by name
   */
  private static final Comparator<BranchConfiguration> CONFIGURATION_COMPARATOR = new Comparator<BranchConfiguration>() {
    @Override
    public int compare(BranchConfiguration o1, BranchConfiguration o2) {
      return o1.NAME.compareTo(o2.NAME);
    }
  };
  /**
   * The comparator for branch information by root
   */
  private static final Comparator<BranchInfo> BRANCH_INFO_COMPARATOR = new Comparator<BranchInfo>() {
    @Override
    public int compare(BranchInfo o1, BranchInfo o2) {
      return o1.ROOT.compareTo(o2.ROOT);
    }
  };
  /**
   * The project
   */
  private final Project myProject;
  /**
   * The git vcs
   */
  private final GitVcs myVcs;
  /**
   * The shelve manager instance
   */
  private final ShelveChangesManager myShelveManager;
  /**
   * The dirty scope manager
   */
  private final VcsDirtyScopeManager myDirtyScopeManager;
  /**
   * Change manager
   */
  private final ChangeListManagerEx myChangeManager;
  /**
   * Project manager
   */
  private final ProjectManagerEx myProjectManager;
  /**
   * The state lock
   */
  private final Object myStateLock = new Object();
  /**
   * The set of configurations
   */
  private final HashMap<String, GitBranchConfiguration> myConfigurations = new HashMap<String, GitBranchConfiguration>();
  /**
   * Create event dispatcher for configuration events
   */
  private final EventDispatcher<GitBranchConfigurationsListener> myListeners =
    EventDispatcher.create(GitBranchConfigurationsListener.class);
  /**
   * The current configuration
   */
  private GitBranchConfiguration myCurrentConfiguration;
  /**
   * The reference listener
   */
  private final GitReferenceListener myReferenceListener;
  /**
   * The current status (cached, invalidated when references change)
   */
  private SpecialStatus myCurrentStatus;
  /**
   * The collection of git roots
   */
  private List<VirtualFile> myGitRoots = Collections.emptyList();
  /**
   * If true, checkout background process is in progress
   */
  private boolean myCheckoutIsInProgress = false;
  /**
   * The widget uninstall action (on deactivate)
   */
  private Runnable myWidgetUninstall;
  /**
   * Listener for changes
   */
  private final ChangeListAdapter myChangesListener;
  /**
   * If true, the widget is enabled
   */
  private boolean myWidgetEnabled = true;

  /**
   * The constructor used to dependency injection
   *
   * @param project           the project
   * @param shelveManager     the shelve manager
   * @param dirtyScopeManager the dirty scope manager
   * @param changeManager     the change manager
   * @param projectManager    the project manager
   */
  public GitBranchConfigurations(Project project, ShelveChangesManager shelveManager,
                                 VcsDirtyScopeManager dirtyScopeManager,
                                 ChangeListManagerEx changeManager,
                                 ProjectManagerEx projectManager) {
    myProject = project;
    myVcs = GitVcs.getInstance(project);
    myShelveManager = shelveManager;
    myDirtyScopeManager = dirtyScopeManager;
    myChangeManager = changeManager;
    myProjectManager = projectManager;
    myReferenceListener = new GitReferenceListener() {
      @Override
      public void referencesChanged(VirtualFile root) {
        GitBranchConfigurations.this.referencesChanged();
      }
    };
    Disposer.register(myProject, this);
    myChangesListener = new ChangeListAdapter() {
      @Override
      public void changesRemoved(Collection<Change> changes, ChangeList fromList) {
        GitBranchConfigurations.this.referencesChanged();
      }

      @Override
      public void changesAdded(Collection<Change> changes, ChangeList toList) {
        GitBranchConfigurations.this.referencesChanged();
      }
    };
  }

  /**
   * Add listener
   *
   * @param l the listener
   */
  public void addConfigurationListener(GitBranchConfigurationsListener l) {
    myListeners.addListener(l);
  }

  /**
   * Remove listener
   *
   * @param l the listener
   */
  public void removeConfigurationListener(GitBranchConfigurationsListener l) {
    myListeners.removeListener(l);
  }

  /**
   * Handle reference change, also notified when roots changed.
   */
  public void referencesChanged() {
    synchronized (myStateLock) {
      updateRootCollection();
      updateSpecialStatus();
      fireReferencesChanged();
    }
  }

  /**
   * Fire that references changed
   */
  private void fireReferencesChanged() {
    myListeners.getMulticaster().referencesChanged();
  }


  /**
   * Update collections of roots
   */
  private void updateRootCollection() {
    try {
      myGitRoots = GitUtil.getGitRoots(myProject, myVcs);
    }
    catch (VcsException e) {
      LOG.warn("Empty list of roots is detected", e);
      myGitRoots = Collections.emptyList();
    }
  }

  /**
   * Update special status
   */
  private void updateSpecialStatus() {
    SpecialStatus p = myCurrentStatus;
    myCurrentStatus = calculateSpecialStatus();
    if (p != myCurrentStatus) {
      myListeners.getMulticaster().specialStatusChanged();
    }
  }

  /**
   * Activate component
   */
  public void activate() {
    myVcs.addGitReferenceListener(myReferenceListener);
    myChangeManager.addChangeListListener(myChangesListener);
    synchronized (myStateLock) {
      updateRootCollection();
      if (myCurrentConfiguration == null) {
        if (calculateSpecialStatus() == SpecialStatus.NORMAL) {
          try {
            detectLocals();
          }
          catch (VcsException e) {
            LOG.error("Exception during detecting local configurations", e);
          }
        }
      }
      referencesChanged();
    }
    if (isWidgetEnabled()) {
      installWidget();
    }
  }

  /**
   * Install widget
   */
  private void installWidget() {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      final Runnable r = GitBranchesWidget.install(myProject, this);
      synchronized (myStateLock) {
        myWidgetUninstall = r;
      }
    }
  }

  /**
   * Deactivate component
   */
  public void deactivate() {
    myVcs.removeGitReferenceListener(myReferenceListener);
    myChangeManager.removeChangeListListener(myChangesListener);
    uninstallWidget();
  }

  private void uninstallWidget() {
    final Runnable r;
    synchronized (myStateLock) {
      r = myWidgetUninstall;
      myWidgetUninstall = null;
    }
    if (r != null) {
      r.run();
    }
  }

  /**
   * Get component instance
   *
   * @param project a context project
   * @return the git settings
   */
  public static GitBranchConfigurations getInstance(Project project) {
    return ServiceManager.getService(project, GitBranchConfigurations.class);
  }


  @Override
  public void dispose() {
    deactivate();
  }


  /**
   * {@inheritDoc}
   */
  @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
  @Override
  public State getState() {
    synchronized (myStateLock) {
      State rc = new State();
      rc.IS_WIDGET_ENABLED = myWidgetEnabled;
      rc.CURRENT = myCurrentConfiguration == null ? null : myCurrentConfiguration.getName();
      ArrayList<BranchConfiguration> cs = new ArrayList<BranchConfiguration>(myConfigurations.size());
      for (GitBranchConfiguration ci : myConfigurations.values()) {
        BranchConfiguration c = new BranchConfiguration();
        c.NAME = ci.getName();
        Map<String, String> map = ci.getReferences();
        ArrayList<BranchInfo> bs = new ArrayList<BranchInfo>(map.size());
        for (Map.Entry<String, String> m : map.entrySet()) {
          BranchInfo b = new BranchInfo();
          b.ROOT = m.getKey();
          b.REFERENCE = m.getValue();
          bs.add(b);
        }
        c.BRANCHES = bs.toArray(new BranchInfo[bs.size()]);
        Arrays.sort(c.BRANCHES, BRANCH_INFO_COMPARATOR);
        c.CHANGES = ci.getChanges();
        c.IS_AUTO_DETECTED = ci.isAutoDetected();
        cs.add(c);
      }
      rc.CONFIGURATIONS = cs.toArray(new BranchConfiguration[cs.size()]);
      Arrays.sort(rc.CONFIGURATIONS, CONFIGURATION_COMPARATOR);
      return rc;
    }
  }

  /**
   * {@inheritDoc}
   */
  @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
  @Override
  public void loadState(State state) {
    synchronized (myStateLock) {
      myConfigurations.clear();
      for (BranchConfiguration bc : state.CONFIGURATIONS) {
        GitBranchConfiguration n = new GitBranchConfiguration(this, bc.NAME);
        myConfigurations.put(n.getName(), n);
        for (BranchInfo bi : bc.BRANCHES) {
          n.setBranch(bi.ROOT, bi.REFERENCE);
        }
        myConfigurations.put(bc.NAME, n);
        n.setAutoDetected(bc.IS_AUTO_DETECTED);
      }
      if (myCurrentConfiguration == null) {
        myCurrentConfiguration = myConfigurations.get(state.CURRENT);
      }
      else {
        myCurrentConfiguration = myConfigurations.get(myCurrentConfiguration.getName());
        if (myCurrentConfiguration == null) {
          myCurrentConfiguration = myConfigurations.get(state.CURRENT);
        }
      }
      fireCurrentConfigurationChanged();
      fireConfigurationsChanged();
      if (state.IS_WIDGET_ENABLED != myWidgetEnabled) {
        myWidgetEnabled = state.IS_WIDGET_ENABLED;
        updateWidgetState();
      }
    }
  }

  /**
   * Update widget state after update
   */
  private void updateWidgetState() {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (isWidgetEnabled()) {
          if (myVcs.isActivated() && myWidgetUninstall == null) {
            installWidget();
          }
        }
        else {
          uninstallWidget();
        }
      }
    });
  }

  /**
   * @return true if widget is enabled
   */
  public boolean isWidgetEnabled() {
    synchronized (myStateLock) {
      return myWidgetEnabled;
    }
  }

  /**
   * Update widget state
   *
   * @param value true to enable widget
   */
  public void setWidgetEnabled(boolean value) {
    synchronized (myStateLock) {
      myWidgetEnabled = value;
      updateWidgetState();
    }
  }


  /**
   * @return the candidate remote configurations
   */
  List<String> getRemotesCandidates() {
    try {
      final List<VirtualFile> roots;
      synchronized (myStateLock) {
        roots = myGitRoots;
      }
      return detectConfigurations(false, roots);
    }
    catch (VcsException e) {
      return Collections.emptyList();
    }
  }

  /**
   * @return the overall special status
   */
  public SpecialStatus getSpecialStatus() {
    synchronized (myStateLock) {
      return myCurrentStatus;
    }
  }

  /**
   * @return the calculated overall special status
   */
  private SpecialStatus calculateSpecialStatus() {
    synchronized (myStateLock) {
      if (myCheckoutIsInProgress) {
        return SpecialStatus.CHECKOUT_IN_PROGRESS;
      }
      for (VirtualFile root : myGitRoots) {
        if (GitRebaseUtils.isRebaseInTheProgress(root)) {
          return SpecialStatus.REBASING;
        }
        if (GitMergeUtil.isMergeInTheProgress(root)) {
          return SpecialStatus.MERGING;
        }
        if (root.findChild(".gitmodules") != null) {
          return SpecialStatus.SUBMODULES;
        }
      }
      for (LocalChangeList changeList : myChangeManager.getChangeListsCopy()) {
        for (Change change : changeList.getChanges()) {
          if (change.getFileStatus() == FileStatus.MERGED_WITH_CONFLICTS) {
            return SpecialStatus.MERGING;
          }
        }
      }
      return myGitRoots.size() == 0 ? SpecialStatus.NON_GIT : SpecialStatus.NORMAL;
    }
  }

  /**
   * Detect local branch configurations
   *
   * @throws VcsException if there is a problem with detecting
   */
  private void detectLocals() throws VcsException {
    synchronized (myStateLock) {
      HashMap<VirtualFile, String> currents = new HashMap<VirtualFile, String>();
      for (VirtualFile root : myGitRoots) {
        GitBranch current = GitBranch.current(myProject, root);
        currents.put(root, current == null ? "" : current.getName());
      }
      if (myConfigurations.isEmpty()) {
        List<String> locals = detectConfigurations(true, myGitRoots);
        if (locals.isEmpty()) {
          // no commits
          locals.add("master");
        }
        for (String localName : locals) {
          GitBranchConfiguration c = createConfiguration(localName);
          c.setAutoDetected(true);
          boolean currentsMatched = true;
          for (VirtualFile root : myGitRoots) {
            c.setBranch(root.getPath(), localName);
            currentsMatched &= currents.get(root).equals(localName);
          }
          if (currentsMatched) {
            myCurrentConfiguration = c;
          }
        }
        if (myCurrentConfiguration == null) {
          // the configuration does not matches any standard, there could be no configurations with spaces at this point
          // since it is not allowed branch name.
          String name = "Unknown 1";
          GitBranchConfiguration c = createConfiguration(name);
          for (VirtualFile root : myGitRoots) {
            c.setBranch(root.getPath(), describeRoot(root));
          }
        }
      }
      fireCurrentConfigurationChanged();
      fireConfigurationsChanged();
    }
  }

  /**
   * The configurations changed
   */
  private void fireConfigurationsChanged() {
    myListeners.getMulticaster().configurationsChanged();
  }

  /**
   * The current configuration changed
   */
  private void fireCurrentConfigurationChanged() {
    myListeners.getMulticaster().currentConfigurationChanged();
  }

  /**
   * Describe vcs root
   *
   * @param root the root to describe
   * @return the current reference
   * @throws VcsException if there is a problem with describing the root
   */
  String describeRoot(VirtualFile root) throws VcsException {
    GitBranch current = GitBranch.current(myProject, root);
    if (current == null) {
      // It is on the tag or specific commit. In future, support for submodules should be added.
      return detectTag(root, "HEAD");
    }
    else {
      return current.getName();
    }
  }

  /**
   * Get tag name for the head
   *
   * @param root the root to describe
   * @param ref  the ref to detect
   * @return the commit expression that describes root state
   */
  String detectTag(VirtualFile root, final String ref) {
    try {
      GitSimpleHandler h = new GitSimpleHandler(myProject, root, GitCommand.DESCRIBE);
      h.addParameters("--tags", "--exact", ref);
      h.setNoSSH(true);
      return h.run().trim();
    }
    catch (VcsException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("describe HEAD failed for root: " + root.getPath());
      }
      try {
        return GitRevisionNumber.resolve(myProject, root, ref).asString();
      }
      catch (VcsException e1) {
        throw new RuntimeException("Unexpected exception at this time, the failure should have been detected at current(): ", e1);
      }
    }
  }

  /**
   * Detect possible configurations
   *
   * @param roots the vcs roots used to detect configuraitons
   * @return a sorted list of branches
   * @throws VcsException if there is a problem with running git
   */
  private List<String> detectConfigurations(boolean local, final List<VirtualFile> roots) throws VcsException {
    HashSet<String> all = new HashSet<String>();
    HashSet<String> forRoot = new HashSet<String>();
    boolean isFirst = true;
    for (VirtualFile root : roots) {
      forRoot.clear();
      GitBranch.listAsStrings(myProject, root, !local, local, forRoot, null);
      if (isFirst) {
        isFirst = false;
        all.addAll(forRoot);
      }
      else {
        all.retainAll(forRoot);
      }
    }
    ArrayList<String> rc = new ArrayList<String>(all);
    Collections.sort(rc);
    return rc;
  }


  /**
   * @return the used vcs
   */
  GitVcs getVcs() {
    return myVcs;
  }

  /**
   * @return the context project
   */
  Project getProject() {
    return myProject;
  }

  /**
   * @return the current configuration
   * @throws VcsException if there is a problem with configurations
   */
  GitBranchConfiguration getCurrentConfiguration() throws VcsException {
    synchronized (myStateLock) {
      if (myCurrentConfiguration == null) {
        throw new VcsException("The current configuration is not yet detected");
      }
      return myCurrentConfiguration;
    }
  }

  /**
   * @return the configuration names
   */
  Set<String> getConfigurationNames() {
    synchronized (myStateLock) {
      return new HashSet<String>(myConfigurations.keySet());
    }
  }

  /**
   * Create new branch configuration
   *
   * @param name the configuration name
   * @return the created configuration
   */
  @NotNull
  GitBranchConfiguration createConfiguration(String name) {
    synchronized (myStateLock) {
      if (myConfigurations.containsKey(name)) {
        throw new IllegalStateException("The name " + name + " is already used");
      }
      GitBranchConfiguration c = new GitBranchConfiguration(this, name);
      myConfigurations.put(name, c);
      fireConfigurationsChanged();
      return c;
    }
  }


  /**
   * Find configuration by name
   *
   * @param name the name to use
   * @return the configuration by name
   * @throws VcsException if there is an error in the state
   */
  @Nullable
  GitBranchConfiguration getConfiguration(String name) throws VcsException {
    synchronized (myStateLock) {
      return myConfigurations.get(name);
    }
  }

  /**
   * @return the state lock for branch configurations
   */
  Object getStateLock() {
    return myStateLock;
  }

  /**
   * Set current configuration
   *
   * @param newConfiguration the new current configuration
   */
  void setCurrentConfiguration(GitBranchConfiguration newConfiguration) {
    synchronized (myStateLock) {
      assert myConfigurations.get(newConfiguration.getName()) == newConfiguration;
      myCurrentConfiguration = newConfiguration;
      fireCurrentConfigurationChanged();
    }
  }

  /**
   * @return the shelve manager from project
   */
  ShelveChangesManager getShelveManager() {
    return myShelveManager;
  }

  /**
   * Remove configuration
   *
   * @param toRemove the removed configuration
   */
  public void removeConfiguration(@NotNull GitBranchConfiguration toRemove) {
    synchronized (myStateLock) {
      if (toRemove == myCurrentConfiguration) {
        throw new IllegalArgumentException("Unable to remove the current configuration");
      }
      myConfigurations.remove(toRemove.getName());
      fireConfigurationsChanged();
    }
  }

  /**
   * Start checkout process for selected configuration in the background
   *
   * @param configuration the selected configuration or null if new configuration is needed.
   * @param remote        the remote pseudo configuration name
   * @param quick         the quick checkout
   */
  public void startCheckout(final GitBranchConfiguration configuration, final String remote, final boolean quick) {
    if (remote != null && configuration != null) {
      throw new IllegalArgumentException("Either remote or configuration to checkout must be null");
    }
    synchronized (myStateLock) {
      final SpecialStatus status = calculateSpecialStatus();
      if (status != SpecialStatus.NORMAL) {
        throw new IllegalStateException("Checkout cannot be started due to special status (it must have been checked in UI): " + status);
      }
      myCheckoutIsInProgress = true;
      updateSpecialStatus();
    }
    final String name = configuration != null ? configuration.getName() : remote == null ? "new configuration" : remote;
    final String title = "Checking out " + name;
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, title, false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          final GitCheckoutProcess process =
            new GitCheckoutProcess(GitBranchConfigurations.this, myProject, myShelveManager, myDirtyScopeManager, myChangeManager,
                                   myProjectManager, indicator, configuration, remote, quick);
          process.run();
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              final List<VcsException> exceptions = process.getExceptions();
              String op = (process.isModify() ? "Modification" : "Checkout") + " of " + name;
              if (!exceptions.isEmpty()) {
                GitUIUtil.showTabErrors(myProject, title, exceptions);
                ToolWindowManager.getInstance(myProject).notifyByBalloon(
                  ChangesViewContentManager.TOOLWINDOW_ID, MessageType.ERROR, op + " failed.");
              }
              else if (process.isCancelled()) {
                ToolWindowManager.getInstance(myProject).notifyByBalloon(
                  ChangesViewContentManager.TOOLWINDOW_ID, MessageType.WARNING, op + " was cancelled by user.");

              }
              else {
                ToolWindowManager.getInstance(myProject).notifyByBalloon(
                  ChangesViewContentManager.TOOLWINDOW_ID, MessageType.INFO, op + " complete.");
              }
            }
          });
        }
        catch (Throwable t) {
          LOG.error("Unexpected exception from checkout: ", t);
        }
        finally {
          synchronized (myStateLock) {
            myCheckoutIsInProgress = false;
            updateSpecialStatus();
          }
        }
      }
    });
  }

  /**
   * Internal notification about renamed configuration
   *
   * @param toRename configuration to rename
   * @param oldName  the old name
   * @param newName  the new name   @return true if configuration actually renamed.
   */
  boolean configurationRenamed(GitBranchConfiguration toRename, String oldName, String newName) {
    synchronized (myStateLock) {
      final GitBranchConfiguration c = myConfigurations.get(oldName);
      if (c == toRename) {
        myConfigurations.remove(oldName);
        myConfigurations.put(newName, c);
      }
      return c == toRename;
    }
  }

  /**
   * The configuration state
   */
  public static class State {
    /**
     * If true, branches widget is enabled
     */
    public boolean IS_WIDGET_ENABLED = true;
    /**
     * The current configuration
     */
    public String CURRENT;
    /**
     * The branch configuration
     */
    public BranchConfiguration[] CONFIGURATIONS = new BranchConfiguration[0];
  }

  /**
   * The branch configuration
   */
  public static class BranchConfiguration {
    /**
     * If true, the configuration was auto-detected
     */
    public boolean IS_AUTO_DETECTED;
    /**
     * The configuration name
     */
    public String NAME;
    /**
     * The branch information
     */
    public BranchInfo[] BRANCHES = new BranchInfo[0];
    /**
     * The branch changes
     */
    public BranchChanges CHANGES;
  }

  /**
   * Branch mapping information
   */
  public static class BranchInfo {
    /**
     * The vcs root for which information is stored
     */
    public String ROOT;
    /**
     * The local branch or specific commit
     */
    public String REFERENCE;
  }

  /**
   * The changes associated with the branch state
   */
  public static class BranchChanges {
    /**
     * The path to shelve that keeps changes
     */
    public String SHELVE_PATH;
    /**
     * Change list information
     */
    public ChangeListInfo[] CHANGE_LISTS = new ChangeListInfo[0];
    /**
     * Information about distribution of changes among change lists
     */
    public ChangeInfo[] CHANGES = new ChangeInfo[0];

  }

  /**
   * The change list information
   */
  public static class ChangeListInfo {
    /**
     * If true, the change list was a default change list
     */
    public boolean IS_DEFAULT = false;
    /**
     * The change list name
     */
    public String NAME;
    /**
     * The change list comment
     */
    public String COMMENT;
  }

  /**
   * Change information. The change is identified by before path and after path (for deleted)
   */
  public static class ChangeInfo {
    /**
     * The before path
     */
    public String BEFORE_PATH;
    /**
     * The after path
     */
    public String AFTER_PATH;
    /**
     * The name of change list to which change belong
     */
    public String CHANGE_LIST_NAME;
  }

  /**
   * The special status for the roots
   */
  public enum SpecialStatus {
    /**
     * Normal work tree, checkout is possible
     */
    NORMAL,
    /**
     * Rebasing
     */
    REBASING,
    /**
     * Merging
     */
    MERGING,
    /**
     * Non git project
     */
    NON_GIT,
    /**
     * The submodules are detected in the project
     */
    SUBMODULES,
    /**
     * The background checkout process is in progress
     */
    CHECKOUT_IN_PROGRESS
  }
}
