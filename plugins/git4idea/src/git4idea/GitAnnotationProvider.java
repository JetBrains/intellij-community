package git4idea;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Author: Anatol Pomozov (Copyright 2008)
 * Copyright 2008 JetBrains s.r.o.
 */

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.commands.GitCommand;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Git annotation provider implementation.
 * <p/>
 * Based on the JetBrains SVNAnnotationProvider.
 */
public class GitAnnotationProvider implements AnnotationProvider {
  /**
   * the context project
   */
  private final Project myProject;
  /**
   * the settings for VCS
   */
  private final GitVcsSettings mySettings;

  /**
   * A constructor
   *
   * @param project  a context project
   * @param settings a settings for the VCS
   */
  public GitAnnotationProvider(@NotNull Project project, @NotNull GitVcsSettings settings) {
    myProject = project;
    mySettings = settings;
  }

  /**
   * {@inheritDoc}
   */
  public FileAnnotation annotate(@NotNull VirtualFile file) throws VcsException {
    GitRevisionNumber gitRev = new GitRevisionNumber(GitRevisionNumber.TIP);
    return annotate(file, new GitFileRevision(myProject, VcsUtil.getFilePath(file.getPath()), gitRev, null, null, null));
  }

  /**
   * {@inheritDoc}
   */
  public FileAnnotation annotate(@NotNull final VirtualFile file, @NotNull VcsFileRevision revision) throws VcsException {
    if (file.isDirectory()) {
      throw new VcsException("Cannot annotate a directory");
    }

    final FileAnnotation[] annotation = new FileAnnotation[1];
    final Exception[] exception = new Exception[1];

    Runnable command = new Runnable() {
      public void run() {
        final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        try {
          GitCommand command = new GitCommand(myProject, mySettings, GitUtil.getVcsRoot(myProject, file));
          FilePath filePath = VcsUtil.getFilePath(file.getPath());
          if (progress != null) {
            progress.setText(GitBundle.message("computing.annotation", file.getName()));
          }
          final GitFileAnnotation result = command.annotate(filePath);

          if (progress != null) {
            progress.setText(GitBundle.message("getting.history", file.getName()));
          }
          // TODO: Optimize it and fetch only log entries that we need.
          final List<VcsFileRevision> revisions = command.log(filePath);

          result.addLogEntries(revisions);
          annotation[0] = result;
        }
        catch (Exception e) {
          exception[0] = e;
        }
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance()
          .runProcessWithProgressSynchronously(command, GitBundle.getString("annotate.action.name"), false, myProject);
    }
    else {
      command.run();
    }
    if (exception[0] != null) {
      throw new VcsException("Failed to annotate: " + exception[0], exception[0]);
    }
    return annotation[0];
  }

  /**
   * {@inheritDoc}
   */
  public boolean isAnnotationValid(VcsFileRevision rev) {
    return true;
  }
}
