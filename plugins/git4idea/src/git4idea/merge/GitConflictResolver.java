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
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.Collection;

/**
 *
 * The class is highly customizable, since the procedure of resolving conflicts is very common in Git operations.
 * @author Kirill Likhodedov
 */
public class GitConflictResolver {

  private static final Logger LOG = Logger.getInstance(GitConflictResolver.class);

  protected final Project myProject;
  private final Collection<VirtualFile> myRoots;
  private final Params myParams;

  private final AbstractVcsHelper myVcsHelper;
  private final GitVcs myVcs;

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

  public GitConflictResolver(@NotNull Project project, @NotNull Collection<VirtualFile> roots, @NotNull Params params) {
    myProject = project;
    myRoots = roots;
    myParams = params;

    myVcsHelper = AbstractVcsHelper.getInstance(project);
    myVcs = GitVcs.getInstance(project);
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
   * @param roots Git repositories to look for unmerged files.
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
  
  protected final boolean mergeNoProceed() {
    return merge(true);
  }

  /**
   * Shows notification that not all conflicts were resolved.
   * @param roots Roots that were merged.
   */
  protected void notifyUnresolvedRemain() {
    notifyWarning(myParams.myErrorNotificationTitle,
                  "You have to <a href='resolve'>resolve</a> all conflicts first." + myParams.myErrorNotificationAdditionalDescription);
  }
  
  private void notifyUnresolvedRemainAfterNotification() {
    notifyWarning("Not all conflicts resolved",
                  "You should <a href='resolve'>resolve</a> all conflicts before update. <br>" +
                  myParams.myErrorNotificationAdditionalDescription);
  }
  
  private void notifyWarning(String title, String content) {
    GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification(title, content, NotificationType.WARNING, new ResolveNotificationListener()).notify(myProject);
  }

  private boolean merge(boolean mergeDialogInvokedFromNotification) {
    try {
      final Collection<VirtualFile> initiallyUnmergedFiles = GitMergeUtil.getUnmergedFiles(myProject, myRoots);
      if (initiallyUnmergedFiles.isEmpty()) {
        LOG.info("merge: no unmerged files");
        return mergeDialogInvokedFromNotification ? true : proceedIfNothingToMerge();
      }
      else {
        showMergeDialog(initiallyUnmergedFiles);

        final Collection<VirtualFile> unmergedFilesAfterResolve = GitMergeUtil.getUnmergedFiles(myProject, myRoots);
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
      notifyException(mergeDialogInvokedFromNotification, e);
    }
    return false;

  }

  private void showMergeDialog(final Collection<VirtualFile> initiallyUnmergedFiles) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override public void run() {
        final MergeProvider mergeProvider = myParams.reverse ? myVcs.getReverseMergeProvider() : myVcs.getMergeProvider();
        myVcsHelper.showMergeDialog(new ArrayList<VirtualFile>(initiallyUnmergedFiles), mergeProvider, myParams.myMergeDialogCustomizer);
      }
    });
  }

  private void notifyException(boolean mergeDialogInvokedFromNotification, VcsException e) {
    LOG.info("mergeFiles ", e);
    final String description = mergeDialogInvokedFromNotification
                               ? "Be sure to resolve all conflicts before update. <br/>"
                               : "Be sure to resolve all conflicts first. ";
    GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification("Not all conflicts resolved",
                                                           description + myParams.myErrorNotificationAdditionalDescription + "<br/>" +
                                                           e.getLocalizedMessage(),
                                                           NotificationType.ERROR,
                                                           new ResolveNotificationListener()).notify(myProject);
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

}
