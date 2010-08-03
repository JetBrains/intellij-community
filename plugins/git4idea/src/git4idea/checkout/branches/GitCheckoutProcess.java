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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.checkout.branches.GitBranchConfigurations.BranchChanges;
import git4idea.checkout.branches.GitBranchConfigurations.ChangeInfo;
import git4idea.checkout.branches.GitBranchConfigurations.ChangeListInfo;
import git4idea.commands.*;
import git4idea.update.GitStashUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * The checkout branch process. It used to organize the entire checkout process
 */
public class GitCheckoutProcess {
  /**
   * The logger
   */
  private static final Logger LOG = Logger.getInstance(GitCheckoutProcess.class.getName());
  /**
   * The configuration process
   */
  final GitBranchConfigurations myConfig;
  /**
   * The vcs roots
   */
  private List<VirtualFile> myRoots;
  /**
   * The vcs exceptions
   */
  private List<VcsException> myExceptions;
  /**
   * The project
   */
  private final Project myProject;
  /**
   * The shelve manager
   */
  private final ShelveChangesManager myShelveManager;
  /**
   * The dirty scope manager
   */
  private final VcsDirtyScopeManager myDirtyScopeManager;
  /**
   * The changes manager
   */
  private final ChangeListManagerEx myChangeManager;
  /**
   * The project manager
   */
  private final ProjectManagerEx myProjectManager;
  /**
   * The progress indicator
   */
  private final ProgressIndicator myProgress;
  /**
   * The name of remote pseudo-configuration
   */
  @Nullable private final String myRemoteConfiguration;
  /**
   * If true, the quick process is being used, and user is not offered to select changes
   */
  private boolean myQuick;
  /**
   * The checkout process is run in the modify mode. Branch name or roots are changed.
   */
  private final boolean myIsModify;
  /**
   * The new configuration
   */
  private GitBranchConfiguration myNewConfiguration;
  /**
   * The new branch mapping
   */
  private Map<VirtualFile, String> myNewBranchMapping = Collections.emptyMap();
  /**
   * The described roots
   */
  private final Map<VirtualFile, String> myDescribedRoots = new HashMap<VirtualFile, String>();
  /**
   * If true, the checkout process was cancelled
   */
  private boolean myCancelled;


  /**
   * The constructor
   *
   * @param config            the configuration object
   * @param project           the context project
   * @param shelveManager     the shelve manager
   * @param dirtyScopeManager the dirty scope manager
   * @param changeManager     the change manager
   * @param projectManager    the project manager
   * @param progress          the progress indicator for the process
   * @param newConfiguration  the  new configuration, the current if modify
   * @param quick             if true, the no changes are assumed to be selected, so dialog need not be shown
   */
  public GitCheckoutProcess(GitBranchConfigurations config,
                            Project project,
                            ShelveChangesManager shelveManager,
                            VcsDirtyScopeManager dirtyScopeManager,
                            ChangeListManagerEx changeManager,
                            ProjectManagerEx projectManager,
                            ProgressIndicator progress,
                            @Nullable GitBranchConfiguration newConfiguration,
                            @Nullable String remoteConfiguration,
                            boolean quick) {
    myExceptions = Collections.synchronizedList(new ArrayList<VcsException>());
    myConfig = config;
    myProject = project;
    myShelveManager = shelveManager;
    myDirtyScopeManager = dirtyScopeManager;
    myChangeManager = changeManager;
    myProjectManager = projectManager;
    myProgress = progress;
    myNewConfiguration = newConfiguration;
    myRemoteConfiguration = remoteConfiguration;
    boolean f = false;
    try {
      f = myNewConfiguration == config.getCurrentConfiguration();
    }
    catch (VcsException e) {
      myExceptions.add(e);
    }
    myIsModify = f;
    myQuick = quick & !myIsModify;
  }


  /**
   * Start the checkout process. The all activity is done in this thread. When needed, the activity is scheduled in awt or other threads.
   */
  public void run() {
    if (!myExceptions.isEmpty()) {
      return;
    }
    myProgress.setText("Staring checkout...");
    try {
      myRoots = GitUtil.getGitRoots(myConfig.getProject(), myConfig.getVcs());
      saveAll();
      waitForChanges();
      myProjectManager.blockReloadingProjectOnExternalChanges();
      try {
        GitBranchConfiguration oldConfiguration = checkCurrentConfiguration();
        if (oldConfiguration == null) {
          myCancelled = true;
          return;
        }
        for (VirtualFile root : myRoots) {
          myDescribedRoots.put(root, myConfig.describeRoot(root));
        }
        ensureNoChangesInCurrent(oldConfiguration);
        if (!myExceptions.isEmpty()) {
          return;
        }
        List<Change> changes = collectChanges();
        Collection<Change> selected;
        myProgress.setText("Verifying the current configuration...");
        if (myQuick && checkRoots()) {
          // show switch dialog with new configuration that allows selecting changes (if not quick switch)
          selected = Collections.emptyList();
        }
        else {
          myProgress.setText("Selecting changes to transfer...");
          selected = selectChangesToTransfer(changes);
          if (selected == null) {
            // the process was cancelled, or no changes that require checkout were made to the current configuration
            myCancelled = true;
            return;
          }
        }
        // preparation phase finished. do actual checkout.
        assert myNewConfiguration != null;
        List<VirtualFile> checkoutRoots = rootsToCheckout();
        if (myNewConfiguration != oldConfiguration || checkoutRoots.size() > 0) {
          // TODO disable saving
          myProgress.setText("Shelving changes...");
          Pair<BranchChanges, BranchChanges> changesPair = shelveChanges(myProgress, oldConfiguration.getName(), selected);
          try {
            // save changes in old root, it also may be aliased with new root
            oldConfiguration.setChanges(changesPair.first);
            try {
              if (checkoutRoots.size() > 0) {
                HashSet<VirtualFile> startedRoots = new HashSet<VirtualFile>();
                boolean failed = !checkoutAndRefreshRoots(checkoutRoots, startedRoots);
                myProgress.setText2("");
                if (!failed) {
                  myConfig.setCurrentConfiguration(myNewConfiguration);
                }
                else {
                  myNewConfiguration = oldConfiguration;
                  rollbackRootCheckout(startedRoots);
                }
              }
              else {
                myConfig.setCurrentConfiguration(myNewConfiguration);
              }
            }
            finally {
              final BranchChanges branchChanges = myNewConfiguration.getChanges();
              myNewConfiguration.setChanges(null);
              restoreChanges(myProgress, branchChanges);
            }
          }
          finally {
            // restore transient shelve
            restoreChanges(myProgress, changesPair.second);
          }
          // TODO enable saving
        }
      }
      finally {
        myProjectManager.unblockReloadingProjectOnExternalChanges();
      }
      // launch project update?
    }
    catch (VcsException e) {
      myExceptions.add(e);
    }
    catch (Throwable e) {
      myExceptions.add(new VcsException("The checkout process failed: " + e.getMessage(), e));
    }
    finally {
      saveAll(); // saves configuration changes
    }
  }

  /**
   * Ensure that current configuration contains no changes. This is a bug condition.
   *
   * @param oldConfiguration the old configuration
   */
  private void ensureNoChangesInCurrent(GitBranchConfiguration oldConfiguration) {
    final BranchChanges oldChanges = oldConfiguration.getChanges();
    if (oldChanges != null) {
      String name = oldChanges.SHELVE_PATH;
      for (ShelvedChangeList changeList : myShelveManager.getShelvedChangeLists()) {
        if (changeList.PATH.equals(oldChanges.SHELVE_PATH)) {
          name = changeList.DESCRIPTION;
          break;
        }
      }
      final VcsException ex = new VcsException("The current configuration contains shelve: " + name);
      LOG.error(ex);
      myExceptions.add(ex);
      oldConfiguration.setChanges(null);
    }
  }

  /**
   * Rollback the root checkout
   *
   * @param startedRoots that roots that has been started and need to be rolled back
   */
  private void rollbackRootCheckout(HashSet<VirtualFile> startedRoots) {
    myProgress.setText("Rolling back...");
    for (VirtualFile root : startedRoots) {
      myProgress.setText2(root.getPath());
      startedRoots.add(root);
      GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.CHECKOUT);
      h.setNoSSH(true);
      h.addParameters("-f", myDescribedRoots.get(root));
      Collection<VcsException> exceptions = GitHandlerUtil.doSynchronouslyWithExceptions(h, myProgress, h.printableCommandLine());
      myExceptions.addAll(exceptions);
    }
    // filter out implicitly included roots
    ArrayList<VirtualFile> newRoots = new ArrayList<VirtualFile>();
    loop:
    for (VirtualFile root : startedRoots) {
      for (VirtualFile other : startedRoots) {
        if (other != root && VfsUtil.isAncestor(other, root, true)) {
          continue loop;
        }
      }
      newRoots.add(root);
    }
    for (VirtualFile root : newRoots) {
      root.refresh(false, true);
    }
    myProgress.setText2("");
  }

  /**
   * Checkout needed vcs roots and do vcs refresh on diff files
   *
   * @param checkoutRoots the roots to checkout
   * @param startedRoots  the roots for which checkout actually started (Modified by this method)
   * @return true if checkout is successful
   * @throws VcsException
   */
  private boolean checkoutAndRefreshRoots(List<VirtualFile> checkoutRoots, HashSet<VirtualFile> startedRoots) throws VcsException {
    boolean failed = false;
    myProgress.setText("Checking out...");
    HashSet<File> filesToRefresh = new HashSet<File>();
    for (VirtualFile root : checkoutRoots) {
      myProgress.setText2(root.getPath());
      startedRoots.add(root);
      GitRevisionNumber prev = GitRevisionNumber.resolve(myProject, root, "HEAD");
      GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.CHECKOUT);
      h.addParameters("-f");
      String branchedRef = myNewBranchMapping.get(root);
      String ref = myNewConfiguration.getReference(root.getPath());
      if (branchedRef != null) {
        h.addParameters("-l", "-t", "-b", ref, branchedRef);
      }
      else {
        h.addParameters(ref);
      }
      Collection<VcsException> exceptions = GitHandlerUtil.doSynchronouslyWithExceptions(h, myProgress, h.printableCommandLine());
      if (!exceptions.isEmpty()) {
        myExceptions.addAll(exceptions);
        failed = true;
        break;
      }
      GitSimpleHandler d = new GitSimpleHandler(myProject, root, GitCommand.DIFF);
      d.addParameters("--name-only", prev.asString() + "..HEAD");
      d.setNoSSH(true);
      d.setSilent(true);
      d.endOptions();
      try {
        File base = new File(root.getPath());
        for (StringScanner s = new StringScanner(d.run()); s.hasMoreData();) {
          String l = s.line();
          if (l.length() > 0) {
            filesToRefresh.add(new File(base, GitUtil.unescapePath(l)));
          }
        }
      }
      catch (VcsException e) {
        LOG.error("Unexpected diff failure", e);
        myExceptions.add(e);
        failed = true;
        break;
      }
    }
    if (!failed) {
      LocalFileSystem.getInstance().refreshIoFiles(filesToRefresh);
    }
    return !failed;
  }

  /**
   * @return set of roots to checkout
   */
  private List<VirtualFile> rootsToCheckout() {
    ArrayList<VirtualFile> rc = new ArrayList<VirtualFile>();
    for (VirtualFile root : myRoots) {
      String current = myDescribedRoots.get(root);
      String newRef = myNewConfiguration.getReference(root.getPath());
      if (!current.equals(newRef)) {
        rc.add(root);
      }
    }
    return rc;
  }

  /**
   * @param changes all changes
   * @return this method allows renaming and updating, the branch configuration and to select changes to transfer to new configuration.
   *         null if process is cancelled, or only name changed in the current configuration and no checkout is required.
   */
  @Nullable
  private Collection<Change> selectChangesToTransfer(final List<Change> changes) {
    final Ref<GitSwitchBranchesDialog.Result> t = new Ref<GitSwitchBranchesDialog.Result>();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          t.set(GitSwitchBranchesDialog
                  .showDialog(myProject, myNewConfiguration, changes, myRoots, myRemoteConfiguration, myConfig, myIsModify));
        }
        catch (VcsException e) {
          myExceptions.add(e);
        }
        catch (Throwable e) {
          LOG.error("Unexpected error", e);
          myExceptions.add(new VcsException("Selecting changes failed", e));
        }
      }
    });
    if (t.get() == null) {
      return null;
    }
    else {
      myNewBranchMapping = t.get().referencesToUse;
      myNewConfiguration = t.get().target;
      return t.get().changes;
    }
  }

  /**
   * @return true if roots in the new configuration are configured correctly
   */
  private boolean checkRoots() {
    if (myNewConfiguration == null) {
      return false;
    }
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    HashSet<VirtualFile> roots = new HashSet<VirtualFile>(myRoots);
    Map<String, String> branches = myNewConfiguration.getReferences();
    for (Map.Entry<String, String> m : branches.entrySet()) {
      VirtualFile root = lfs.findFileByPath(m.getKey());
      if (root == null || root.findChild(".git") == null || !roots.contains(root)) {
        return false;
      }
      roots.remove(root);
      try {
        GitRevisionNumber.resolve(myProject, root, m.getValue());
      }
      catch (VcsException e) {
        return false;
      }
    }
    return roots.isEmpty();
  }

  /**
   * @return collect changes from project
   */
  private List<Change> collectChanges() {
    List<LocalChangeList> changeLists = myChangeManager.getChangeLists();
    ArrayList<Change> changes = new ArrayList<Change>();
    for (LocalChangeList l : changeLists) {
      changes.addAll(l.getChanges());
    }
    return changes;
  }


  /**
   * @return the current configuration, or null if process was cancelled
   * @throws VcsException if there is a problem with checking configuration
   */
  @Nullable
  private GitBranchConfiguration checkCurrentConfiguration() throws VcsException {
    GitBranchConfiguration c = myConfig.getCurrentConfiguration();
    if (!myIsModify && !rootsMatchConfiguration(c)) {
      // when the current configuration is modified, there is no need to show duplicate dialogs
      c = GitBranchConfigurationChangedDialog.showDialog(myConfig, c, myRoots);
    }
    return c;
  }

  /**
   * Check of the root mapping has been changed for the current vcs root
   *
   * @param c the configuration to check
   * @return true if nothing shoudlbe checked out
   * @throws VcsException
   */
  private boolean rootsMatchConfiguration(GitBranchConfiguration c) throws VcsException {
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    Map<String, String> branches = c.getReferences();
    if (branches.size() != myRoots.size()) {
      return false;
    }
    HashSet<String> realSet = new HashSet<String>();
    for (VirtualFile root : myRoots) {
      realSet.add(root.getPath());
    }
    HashSet<String> storedSet = new HashSet<String>();
    for (Map.Entry<String, String> m : branches.entrySet()) {
      String rootPath = m.getKey();
      storedSet.add(rootPath);
      VirtualFile root = lfs.findFileByPath(rootPath);
      String ref = myConfig.describeRoot(root);
      if (!ref.equals(m.getValue())) {
        return false;
      }
    }
    return storedSet.equals(realSet);
  }

  private void waitForChanges() throws VcsException {
    final Semaphore s = new Semaphore(0);
    waitForChangesRefresh("Preparing for the checkout: ", new Runnable() {
      @Override
      public void run() {
        s.release();
      }
    });
    try {
      s.acquire();
    }
    catch (InterruptedException e) {
      throw new VcsException("Waiting for changes was interrupted: ", e);
    }
  }


  /**
   * Save all changes before start of update process
   */
  private static void saveAll() {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            FileDocumentManager.getInstance().saveAllDocuments();
          }
        });
      }
    });
  }

  /**
   * Restore changes.
   *
   * @param progress the progress indicator
   * @param changes  the changes to restore, null means no changes to restore
   * @return true if changes has been restored successfully
   */
  private boolean restoreChanges(ProgressIndicator progress,
                                 final BranchChanges changes) {
    if (changes == null) {
      return true;
    }
    ShelvedChangeList shelve = null;
    for (ShelvedChangeList changeList : myShelveManager.getShelvedChangeLists()) {
      if (changeList.PATH.equals(changes.SHELVE_PATH)) {
        shelve = changeList;
      }
    }
    if (shelve == null) {
      //noinspection ThrowableInstanceNeverThrown
      myExceptions.add(new VcsException("Failed to find shelve with path" + changes.SHELVE_PATH));
      return false;
    }
    progress.setText("Refreshing files before restoring shelve: " + shelve.DESCRIPTION);
    GitStashUtils.doSystemUnshelve(myProject, shelve, myShelveManager, myChangeManager, myExceptions);
    // dirty files and parse changes
    final HashMap<Pair<String, String>, String> parsedChanges = new HashMap<Pair<String, String>, String>();
    for (ChangeInfo changeInfo : changes.CHANGES) {
      String before = changeInfo.BEFORE_PATH;
      String after = changeInfo.AFTER_PATH;
      parsedChanges.put(Pair.create(before, after), changeInfo.CHANGE_LIST_NAME);
      if (after != null) {
        myDirtyScopeManager.fileDirty(VcsUtil.getFilePath(after));
      }
      if (before != null) {
        myDirtyScopeManager.fileDirty(VcsUtil.getFilePath(before));
      }
    }
    final ShelvedChangeList finalShelve = shelve;
    try {
      waitForChanges();
      HashMap<String, LocalChangeList> lists = new HashMap<String, LocalChangeList>();
      for (LocalChangeList localChangeList : myChangeManager.getChangeLists()) {
        lists.put(localChangeList.getName(), localChangeList);
      }
      LocalChangeList defaultList = myChangeManager.getDefaultChangeList();
      for (ChangeListInfo changeListInfo : changes.CHANGE_LISTS) {
        LocalChangeList changeList = lists.get(changeListInfo.NAME);
        if (changeList == null) {
          changeList = myChangeManager.addChangeList(changeListInfo.NAME, changeListInfo.COMMENT);
          lists.put(changeListInfo.NAME, changeList);
        }
        if (changeListInfo.IS_DEFAULT) {
          myChangeManager.setDefaultChangeList(changeList);
        }
      }
      for (Change change : defaultList.getChanges()) {
        ContentRevision beforeRevision = change.getBeforeRevision();
        String before = beforeRevision == null ? null : beforeRevision.getFile().getPath();
        ContentRevision afterRevision = change.getAfterRevision();
        String after = afterRevision == null ? null : afterRevision.getFile().getPath();
        Pair<String, String> key = Pair.create(before, after);
        String listName = parsedChanges.get(key);
        assert listName != null : "List name should be found: " + key;
        if (!listName.equals(defaultList.getName())) {
          LocalChangeList changeList = lists.get(listName);
          assert changeList != null : "Change List should be found: " + listName;
          myChangeManager.moveChangesTo(changeList, new Change[]{change});
        }
      }
      return true;
    }
    catch (Throwable t) {
      //noinspection ThrowableInstanceNeverThrown
      myExceptions.add(
        new VcsException("Failed to process restore shelved change list: " + finalShelve.DESCRIPTION + ". Please restore it manually.", t));
      return false;
    }
  }

  /**
   * Wait until changes are refreshed
   *
   * @param title    the title of the operation
   * @param runnable the process that awaits changes
   */
  void waitForChangesRefresh(final String title, final Runnable runnable) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        myChangeManager.invokeAfterUpdate(runnable, InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE, title,
                                          ModalityState.NON_MODAL);
      }
    });
  }

  /**
   * Shelve selected and transient changes. If creation of one of shelves fails, the other shelve is rolled back
   *
   * @param configurationName the current configuration name
   * @param selectedChanges   the selected changes
   * @return null if operation fails or pair if operation completed successfully. The first is changes to be stored in old configuration, the second is trainsient changes.
   */
  @Nullable
  private Pair<BranchChanges, BranchChanges> shelveChanges(ProgressIndicator progress,
                                                           String configurationName,
                                                           Collection<Change> selectedChanges) {
    assert myExceptions.isEmpty() : "The method should not be called if there is already problems detected";
    List<LocalChangeList> changeLists = myChangeManager.getChangeListsCopy();
    HashMap<Change, LocalChangeList> changes = new HashMap<Change, LocalChangeList>();
    for (LocalChangeList l : changeLists) {
      for (Change change : l.getChanges()) {
        changes.put(change, l);
      }
    }
    HashSet<Change> selected = new HashSet<Change>(selectedChanges);
    HashSet<Change> other = new HashSet<Change>(changes.keySet());
    other.removeAll(selected);

    Date now = new Date();
    BranchChanges storedChanges = shelveChanges(progress, changes, other,
                                                "Shelved changes for configuration " + configurationName + " (created on " + now + ")");
    if (!myExceptions.isEmpty()) {
      return null;
    }
    BranchChanges transientChanges = shelveChanges(progress, changes, selected,
                                                   "Transferred changes from configuration " +
                                                   configurationName +
                                                   " (created on " +
                                                   now +
                                                   ")");
    if (!myExceptions.isEmpty()) {
      restoreChanges(progress, storedChanges);
      return null;
    }
    return Pair.create(storedChanges, transientChanges);
  }

  /**
   * Shelve changes remembering change configuration
   *
   * @param progress    the progress
   * @param changes     the all changes to process
   * @param toShelve    the shelved change subset
   * @param description the description of the shelve
   * @return branch change set or null if shelve is not created (there will be exceptions in list in case of errors)
   */
  @Nullable
  private BranchChanges shelveChanges(ProgressIndicator progress,
                                      HashMap<Change, LocalChangeList> changes,
                                      HashSet<Change> toShelve, String description
  ) {
    if (toShelve.isEmpty()) {
      return null;
    }
    HashSet<LocalChangeList> lists = new HashSet<LocalChangeList>();
    ArrayList<ChangeInfo> ci = new ArrayList<ChangeInfo>(toShelve.size());
    for (Change c : toShelve) {
      LocalChangeList l = changes.get(c);
      lists.add(l);
      ChangeInfo i = new ChangeInfo();
      ContentRevision after = c.getAfterRevision();
      if (after != null) {
        i.AFTER_PATH = after.getFile().getPath();
      }
      ContentRevision before = c.getBeforeRevision();
      if (before != null) {
        i.BEFORE_PATH = before.getFile().getPath();
      }
      i.CHANGE_LIST_NAME = l.getName();
      ci.add(i);
    }
    ArrayList<ChangeListInfo> li = new ArrayList<ChangeListInfo>(lists.size());
    for (LocalChangeList l : lists) {
      ChangeListInfo i = new ChangeListInfo();
      i.IS_DEFAULT = l.isDefault();
      i.NAME = l.getName();
      i.COMMENT = l.getComment();
      li.add(i);
    }
    if (progress != null) {
      progress.setText("Creating shelve: " + description);
    }
    ShelvedChangeList shelved = GitStashUtils.shelveChanges(myProject, myShelveManager, toShelve, description, myExceptions);
    if (shelved == null) {
      return null;
    }
    BranchChanges b = new BranchChanges();
    b.SHELVE_PATH = shelved.PATH;
    b.CHANGE_LISTS = li.toArray(new ChangeListInfo[li.size()]);
    b.CHANGES = ci.toArray(new ChangeInfo[ci.size()]);
    return b;
  }

  /**
   * @return the list of problems
   */
  public List<VcsException> getExceptions() {
    return myExceptions;
  }

  /**
   * @return true if the process was cancelled
   */
  public boolean isCancelled() {
    return myCancelled;
  }

  /**
   * @return true if the process was modification process
   */
  public boolean isModify() {
    return myIsModify;
  }
}
