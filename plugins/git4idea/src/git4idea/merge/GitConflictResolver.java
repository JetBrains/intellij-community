/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.merge;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.*;

/**
 *
 * The class is highly customizable, since the procedure of resolving conflicts is very common in Git operations.
 * @author Kirill Likhodedov
 */
public class GitConflictResolver {

  private static final Logger LOG = Logger.getInstance(GitConflictResolver.class);

  @NotNull protected final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final GitPlatformFacade myPlatformFacade;
  private final Collection<VirtualFile> myRoots;
  private final Params myParams;

  @NotNull private final GitRepositoryManager myRepositoryManager;
  private final AbstractVcsHelper myVcsHelper;

  /**
   * Customizing parameters - mostly String notification texts, etc.
   */
  public static class Params {
    private boolean reverse;
    private String myErrorNotificationTitle = "";
    private String myErrorNotificationAdditionalDescription = "";
    private String myMergeDescription = "";
    private MergeDialogCustomizer myMergeDialogCustomizer = new MergeDialogCustomizer() {
      @Override public String getMultipleFileMergeDescription(Collection<VirtualFile> files) {
        return myMergeDescription;
      }
    };

    /**
     * @param reverseMerge specify {@code true} if reverse merge provider has to be used for merging - it is the case of rebase or stash.
     */
    public Params setReverse(boolean reverseMerge) {
      reverse = reverseMerge;
      return this;
    }

    public Params setErrorNotificationTitle(String errorNotificationTitle) {
      myErrorNotificationTitle = errorNotificationTitle;
      return this;
    }

    public Params setErrorNotificationAdditionalDescription(String errorNotificationAdditionalDescription) {
      myErrorNotificationAdditionalDescription = errorNotificationAdditionalDescription;
      return this;
    }

    public Params setMergeDescription(String mergeDescription) {
      myMergeDescription = mergeDescription;
      return this;
    }

    public Params setMergeDialogCustomizer(MergeDialogCustomizer mergeDialogCustomizer) {
      myMergeDialogCustomizer = mergeDialogCustomizer;
      return this;
    }
    
  }

  public GitConflictResolver(@NotNull Project project, @NotNull Git git, @NotNull GitPlatformFacade platformFacade,
                             @NotNull Collection<VirtualFile> roots, @NotNull Params params) {
    myProject = project;
    myGit = git;
    myPlatformFacade = platformFacade;
    myRoots = roots;
    myParams = params;
    myRepositoryManager = myPlatformFacade.getRepositoryManager(myProject);
    myVcsHelper = myPlatformFacade.getVcsHelper(project);
  }

  /**
   * <p>
   *   Goes throw the procedure of merging conflicts via MergeTool for different types of operations.
   *   <ul>
   *     <li>Checks if there are unmerged files. If not, executes {@link #proceedIfNothingToMerge()}</li>
   *     <li>Otherwise shows a {@link com.intellij.openapi.vcs.merge.MultipleFileMergeDialog} where user is able to merge files.</li>
   *     <li>After the dialog is closed, checks if unmerged files remain.
   *         If everything is merged, executes {@link #proceedAfterAllMerged()}. Otherwise shows a notification.</li>
   *   </ul>
   * </p>
   * <p>
   *   If a Git error happens during seeking for unmerged files or in other cases,
   *   the method shows a notification and returns {@code false}.
   * </p>
   *
   * @return {@code true} if there is nothing to merge anymore, {@code false} if unmerged files remain or in the case of error.
   */
  public final boolean merge() {
    return merge(false);
  }

  /**
   * This is executed from {@link #merge()} if the initial check tells that there is nothing to merge.
   * In the basic implementation no action is performed, {@code true} is returned.
   * @return Return value is returned from {@link #merge()}
   */
  protected boolean proceedIfNothingToMerge() throws VcsException {
    return true;
  }

  /**
   * This is executed from {@link #merge()} after all conflicts are resolved.
   * In the basic implementation no action is performed, {@code true} is returned.
   * @return Return value is returned from {@link #merge()}
   */
  protected boolean proceedAfterAllMerged() throws VcsException {
    return true;
  }

  /**
   * Invoke the merge dialog, but execute nothing after merge is completed.
   * @return true if all changes were merged, false if unresolved merges remain.
   */
  public final boolean mergeNoProceed() {
    return merge(true);
  }

  /**
   * Shows notification that not all conflicts were resolved.
   */
  protected void notifyUnresolvedRemain() {
    notifyWarning(myParams.myErrorNotificationTitle,
                  "You have to <a href='resolve'>resolve</a> all conflicts first." + myParams.myErrorNotificationAdditionalDescription);
  }

  /**
   * Shows notification that some conflicts were still not resolved - after user invoked the conflict resolver by pressing the link on the
   * notification.
   */
  private void notifyUnresolvedRemainAfterNotification() {
    notifyWarning("Not all conflicts resolved",
                  "You should <a href='resolve'>resolve</a> all conflicts before update. <br>" +
                  myParams.myErrorNotificationAdditionalDescription);
  }
  
  private void notifyWarning(String title, String content) {
    myPlatformFacade.getNotificator(myProject).notifyStrongWarning(title, content, new ResolveNotificationListener());
  }

  private boolean merge(boolean mergeDialogInvokedFromNotification) {
    try {
      final Collection<VirtualFile> initiallyUnmergedFiles = getUnmergedFiles(myRoots);
      if (initiallyUnmergedFiles.isEmpty()) {
        LOG.info("merge: no unmerged files");
        return mergeDialogInvokedFromNotification ? true : proceedIfNothingToMerge();
      }
      else {
        showMergeDialog(initiallyUnmergedFiles);

        final Collection<VirtualFile> unmergedFilesAfterResolve = getUnmergedFiles(myRoots);
        if (unmergedFilesAfterResolve.isEmpty()) {
          LOG.info("merge no more unmerged files");
          return mergeDialogInvokedFromNotification ? true : proceedAfterAllMerged();
        } else {
          LOG.info("mergeFiles unmerged files remain: " + unmergedFilesAfterResolve);
          if (mergeDialogInvokedFromNotification) {
            notifyUnresolvedRemainAfterNotification();
          } else {
            notifyUnresolvedRemain();
          }
        }
      }
    } catch (VcsException e) {
      if (((GitVcs)myPlatformFacade.getVcs(myProject)).getExecutableValidator().checkExecutableAndNotifyIfNeeded()) {
        notifyException(e);
      }
    }
    return false;

  }

  private void showMergeDialog(final Collection<VirtualFile> initiallyUnmergedFiles) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override public void run() {
        final MergeProvider mergeProvider = myParams.reverse ?
                                            new GitMergeProvider(myProject, true) : new GitMergeProvider(myProject, false);
        myVcsHelper.showMergeDialog(new ArrayList<VirtualFile>(initiallyUnmergedFiles), mergeProvider, myParams.myMergeDialogCustomizer);
      }
    });
  }

  private void notifyException(VcsException e) {
    LOG.info("mergeFiles ", e);
    final String description = "Couldn't check the working tree for unmerged files because of an error.";
    GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification(myParams.myErrorNotificationTitle,
                                                           description + myParams.myErrorNotificationAdditionalDescription + "<br/>" +
                                                           e.getLocalizedMessage(),
                                                           NotificationType.ERROR,
                                                           new ResolveNotificationListener()).notify(myProject);
  }


  @NotNull
  protected NotificationListener getResolveLinkListener() {
    return new ResolveNotificationListener();
  }

  private class ResolveNotificationListener implements NotificationListener {
    @Override public void hyperlinkUpdate(@NotNull final Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equals("resolve")) {
        notification.expire();
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override public void run() {
            mergeNoProceed();
          }
        });
      }
    }
  }

  /**
   * @return unmerged files in the given Git roots, all in a single collection.
   * @see #getUnmergedFiles(com.intellij.openapi.vfs.VirtualFile)
   */
  private Collection<VirtualFile> getUnmergedFiles(@NotNull Collection<VirtualFile> roots) throws VcsException {
    final Collection<VirtualFile> unmergedFiles = new HashSet<VirtualFile>();
    for (VirtualFile root : roots) {
      unmergedFiles.addAll(getUnmergedFiles(root));
    }
    return unmergedFiles;
  }

  /**
   * @return unmerged files in the given Git root.
   * @see #getUnmergedFiles(java.util.Collection
   */
  private Collection<VirtualFile> getUnmergedFiles(@NotNull VirtualFile root) throws VcsException {
    return unmergedFiles(root);
  }

  /**
   * Parse changes from lines
   *
   *
   * @param root    the git root
   * @return a set of unmerged files
   * @throws com.intellij.openapi.vcs.VcsException if the input format does not matches expected format
   */
  private List<VirtualFile> unmergedFiles(VirtualFile root) throws VcsException {
    HashSet<VirtualFile> unmerged = new HashSet<VirtualFile>();
    String rootPath = root.getPath();

    GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) {
      LOG.error("Repository not found for root " + root);
      return Collections.emptyList();
    }

    GitCommandResult result = myGit.getUnmergedFiles(repository);
    if (!result.success()) {
      throw new VcsException(result.getErrorOutputAsJoinedString());
    }

    String output = StringUtil.join(result.getOutput(), "\n");

    LocalFileSystem lfs = myPlatformFacade.getLocalFileSystem();
    for (StringScanner s = new StringScanner(output); s.hasMoreData();) {
      if (s.isEol()) {
        s.nextLine();
        continue;
      }
      s.boundedToken('\t');
      final String relative = s.line();
      String path = rootPath + "/" + GitUtil.unescapePath(relative);
      VirtualFile file = lfs.refreshAndFindFileByPath(path);
      if (file != null) {
      // the file name is in the delete- or rename- conflict, so it is shown in the list of unmerged files,
      // but the file itself doesn't exist. In that case we just ignore the file.
        file.refresh(false, false);
        unmerged.add(file);
      }
    }
    if (unmerged.size() == 0) {
      return Collections.emptyList();
    }
    else {
      ArrayList<VirtualFile> rc = new ArrayList<VirtualFile>(unmerged.size());
      rc.addAll(unmerged);
      Collections.sort(rc, GitUtil.VIRTUAL_FILE_COMPARATOR);
      return rc;
    }
  }

}
