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
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitVcs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Kirill Likhodedov
 */
public class GitMergeConflictResolver {

  private final Project myProject;
  private final boolean myReverseMerge;
  private final String myErrorNotificationTitle;
  private final String myErrorNotificationAdditionalDescription;
  private final AbstractVcsHelper myVcsHelper;
  private final GitVcs myVcs;

  /**
   * @param reverseMerge specify if reverse merge provider has to be used for merging - it is the case of rebase or stash.
   */
  public GitMergeConflictResolver(@NotNull Project project, boolean reverseMerge, @NonNls String errorNotificationTitle, @NotNull String errorNotificationAdditionalDescription) {
    myProject = project;
    myReverseMerge = reverseMerge;
    myErrorNotificationTitle = errorNotificationTitle;
    myErrorNotificationAdditionalDescription = errorNotificationAdditionalDescription;
    myVcsHelper = AbstractVcsHelper.getInstance(project);
    myVcs = GitVcs.getInstance(project);
  }

  /**
   * Goes throw the procedure of merging conflicts via MergeTool for different types of operations.
   *
   * 1. Checks if there are unmerged files. If not, executes {@link #proceedIfNothingToMerge()}
   * 2. Otherwise shows {@link com.intellij.openapi.vcs.merge.MultipleFileMergeDialog} where user merges files.
   * 3. After dialog is closed, checks if unmerged files remain. If not, executes {@link #proceedAfterAllMerged()}.
   * Otherwise shows a notification.
   *
   * @param roots Git repositories to look for unmerged files.
   * @return true if there is nothing to merge anymore, false if unmerged files remain or in the case of error.
   */
  public final boolean mergeFiles(@NotNull final Collection<VirtualFile> roots) {
    try {
      Collection<VirtualFile> unmergedFiles = GitMergeUtil.getUnmergedFiles(myProject, roots);
      if (unmergedFiles.isEmpty()) {
        return proceedIfNothingToMerge();
      } else {
        // TODO add descriptive message to the dialog:
        // You must resolve all conflicts before you continue rebase
        final Collection<VirtualFile> finalUnmergedFiles = unmergedFiles;
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override public void run() {
            final MergeProvider mergeProvider = myReverseMerge ? myVcs.getReverseMergeProvider() : myVcs.getMergeProvider();
            myVcsHelper.showMergeDialog(new ArrayList<VirtualFile>(finalUnmergedFiles), mergeProvider);
          }
        });

        unmergedFiles = GitMergeUtil.getUnmergedFiles(myProject, roots);
        if (unmergedFiles.isEmpty()) {
          return proceedAfterAllMerged();
        } else {
          Notifications.Bus.notify(new Notification(GitVcs.IMPORTANT_ERROR_NOTIFICATION, myErrorNotificationTitle,
                                                    "You have to <a href='resolve'>resolve</a> all conflicts first." +
                                                    myErrorNotificationAdditionalDescription, NotificationType.WARNING,
                                                    new ResolveNotificationListener(roots)), myProject);
        }
      }
    } catch (VcsException e) {
      Notifications.Bus.notify(new Notification(GitVcs.IMPORTANT_ERROR_NOTIFICATION, myErrorNotificationTitle,
                                                "Be sure to resolve all conflicts first. " +
                                                myErrorNotificationAdditionalDescription + "<br/>" +
                                                e.getLocalizedMessage(), NotificationType.ERROR), myProject);
    }
    return false;
  }

  /**
   * Does the same as {@link #mergeFiles(java.util.Collection)}, but just returns the result of merging without proceeding with update
   * or other operation. Also notifications are a bit different.
   * @return true if all conflicts were merged, false if unmerged files remain or in the case of error.
   */
  private boolean justMerge(@NotNull final Collection<VirtualFile> roots) {
    try {
      Collection<VirtualFile> unmergedFiles = GitMergeUtil.getUnmergedFiles(myProject, roots);
      if (unmergedFiles.isEmpty()) {
        return true;
      } else {
        final Collection<VirtualFile> finalUnmergedFiles = unmergedFiles;
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override public void run() {
            final MergeProvider mergeProvider = myReverseMerge ? myVcs.getReverseMergeProvider() : myVcs.getMergeProvider();
            myVcsHelper.showMergeDialog(new ArrayList<VirtualFile>(finalUnmergedFiles), mergeProvider);
          }
        });

        unmergedFiles = GitMergeUtil.getUnmergedFiles(myProject, roots);
        if (unmergedFiles.isEmpty()) {
          return true;
        } else {
          Notifications.Bus.notify(new Notification(GitVcs.NOTIFICATION_GROUP_ID, "Not all conflicts resolved",
                                                    "You should <a href='resolve'>resolve</a> all conflicts before update. <br/>" +
                                                    myErrorNotificationAdditionalDescription, NotificationType.WARNING, new ResolveNotificationListener(roots)), myProject);
        }
      }
    } catch (VcsException e) {
      Notifications.Bus.notify(new Notification(GitVcs.NOTIFICATION_GROUP_ID, "Not all conflicts resolved",
                                                "Be sure to resolve all conflicts before update. <br/>" +
                                                myErrorNotificationAdditionalDescription + "<br/>" +
                                                e.getLocalizedMessage(), NotificationType.ERROR), myProject);
    }
    return false;
  }

  /**
   * This is executed from {@link #mergeFiles(java.util.Collection)} if the initial check tells that there is nothing to merge.
   * @return Return value is returned from {@link #mergeFiles(java.util.Collection)}
   */
  protected boolean proceedIfNothingToMerge() throws VcsException {
    return true;
  }

  /**
   * This is executed from {@link #mergeFiles(java.util.Collection)} after all conflicts are resolved.
   * @return Return value is returned from {@link #mergeFiles(java.util.Collection)}
   */
  protected boolean proceedAfterAllMerged() throws VcsException {
    return true;
  }

  private class ResolveNotificationListener implements NotificationListener {
    private final Collection<VirtualFile> myRoots;

    public ResolveNotificationListener(Collection<VirtualFile> roots) {
      myRoots = roots;
    }

    @Override public void hyperlinkUpdate(@NotNull final Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equals("resolve")) {
        notification.expire();
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override public void run() {
            justMerge(myRoots);
          }
        });
      }
    }
  }
}
