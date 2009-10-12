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
package git4idea.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.util.ui.ColumnInfo;
import git4idea.actions.GitShowAllSubmittedFilesAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Git history provider implementation
 */
public class GitHistoryProvider implements VcsHistoryProvider {
  /**
   * logger instance
   */
  private static final Logger log = Logger.getInstance(GitHistoryProvider.class.getName());
  /**
   * the current project instance
   */
  private final Project project;

  /**
   * A constructor
   *
   * @param project a context project
   */
  public GitHistoryProvider(@NotNull Project project) {
    this.project = project;
  }

  /**
   * {@inheritDoc}
   */
  public VcsDependentHistoryComponents getUICustomization(final VcsHistorySession session, JComponent forShortcutRegistration) {
    return VcsDependentHistoryComponents.createOnlyColumns(new ColumnInfo[0]);
  }

  /**
   * {@inheritDoc}
   */
  public AnAction[] getAdditionalActions(FileHistoryPanel panel) {
    return new AnAction[]{new GitShowAllSubmittedFilesAction()};
  }

  /**
   * {@inheritDoc}
   */
  public boolean isDateOmittable() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public String getHelpId() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public VcsHistorySession createSessionFor(final FilePath filePath) throws VcsException {
    if (filePath.isDirectory()) {
      return null;
    }
    List<VcsFileRevision> revisions = GitHistoryUtils.history(project, filePath);
    return new VcsHistorySession(revisions) {
      @Nullable
      protected VcsRevisionNumber calcCurrentRevisionNumber() {
        try {
          return GitHistoryUtils.getCurrentRevision(project, GitHistoryUtils.getLastCommitName(project, filePath));
        }
        catch (VcsException e) {
          // likely the file is not under VCS anymore.
          if (log.isDebugEnabled()) {
            log.debug("Unable to retrieve the current revision number", e);
          }
          return null;
        }
      }
    };
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public HistoryAsTreeProvider getTreeHistoryProvider() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public boolean supportsHistoryForDirectories() {
    return false;
  }
}
