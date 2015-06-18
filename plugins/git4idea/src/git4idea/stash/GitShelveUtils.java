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
package git4idea.stash;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.AsynchronousExecution;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFile;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChange;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GitShelveUtils {
  private static final Logger LOG = Logger.getInstance(GitShelveUtils.class.getName());

  /**
   * Perform system level unshelve operation
   *
   * @param project           the project
   * @param shelvedChangeList the shelved change list
   * @param shelveManager     the shelve manager
   */
  @AsynchronousExecution
  public static void doSystemUnshelve(final Project project, final ShelvedChangeList shelvedChangeList,
                                      final ShelveChangesManager shelveManager,
                                      final @NotNull ContinuationContext context,
                                      @Nullable final String leftConflictTitle, @Nullable final String rightConflictTitle) {
    VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;
    final String projectPath = baseDir.getPath() + "/";

    context.next(new TaskDescriptor("Refreshing files before unshelve", Where.POOLED) {
        @Override
        public void run(ContinuationContext context) {
          LOG.info("doSystemUnshelve ");
          // The changes are temporary copied to the first local change list, the next operation will restore them back
          // Refresh files that might be affected by unshelve
          refreshFilesBeforeUnshelve(project, shelvedChangeList, projectPath);

          LOG.info("doSystemUnshelve files refreshed. unshelving in AWT thread.");
        }
      }, new TaskDescriptor("", Where.AWT) {
        @Override
        public void run(ContinuationContext context) {
          LOG.info("Unshelving in UI thread. shelvedChangeList: " + shelvedChangeList);
          // we pass null as target change list for Patch Applier to do NOTHING with change lists
          shelveManager.scheduleUnshelveChangeList(shelvedChangeList, shelvedChangeList.getChanges(project),
                                                   shelvedChangeList.getBinaryFiles(), null, false, context, true,
                                                   true, leftConflictTitle, rightConflictTitle);
        }
      });
  }

  public static void refreshFilesBeforeUnshelve(final Project project, ShelvedChangeList shelvedChangeList, String projectPath) {
    HashSet<File> filesToRefresh = new HashSet<File>();
    for (ShelvedChange c : shelvedChangeList.getChanges(project)) {
      if (c.getBeforePath() != null) {
        filesToRefresh.add(new File(projectPath + c.getBeforePath()));
      }
      if (c.getAfterPath() != null) {
        filesToRefresh.add(new File(projectPath + c.getAfterPath()));
      }
    }
    for (ShelvedBinaryFile f : shelvedChangeList.getBinaryFiles()) {
      if (f.BEFORE_PATH != null) {
        filesToRefresh.add(new File(projectPath + f.BEFORE_PATH));
      }
      if (f.AFTER_PATH != null) {
        filesToRefresh.add(new File(projectPath + f.AFTER_PATH));
      }
    }
    LocalFileSystem.getInstance().refreshIoFiles(filesToRefresh);
  }

  /**
   * Shelve changes
   *
   *
   * @param project       the context project
   * @param shelveManager the shelve manager
   * @param changes       the changes to process
   * @param description   the description of for the shelve
   * @param exceptions    the generated exceptions
   * @param rollback
   * @return created shelved change list or null in case failure
   */
  @Nullable
  public static ShelvedChangeList shelveChanges(final Project project, final ShelveChangesManager shelveManager, Collection<Change> changes,
                                                final String description,
                                                final List<VcsException> exceptions, boolean rollback) {
    try {
      ShelvedChangeList shelve = shelveManager.shelveChanges(changes, description, rollback);
      project.getMessageBus().syncPublisher(ShelveChangesManager.SHELF_TOPIC).stateChanged(new ChangeEvent(GitStashUtils.class));
      return shelve;
    }
    catch (IOException e) {
      //noinspection ThrowableInstanceNeverThrown
      exceptions.add(new VcsException("Shelving changes failed: " + description, e));
      return null;
    }
    catch (VcsException e) {
      exceptions.add(e);
      return null;
    }
  }
}
