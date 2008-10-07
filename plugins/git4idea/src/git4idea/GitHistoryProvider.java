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
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Copyright 2008 JetBrains s.r.o.
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.*;
import com.intellij.util.ui.ColumnInfo;
import git4idea.commands.GitCommand;
import git4idea.config.GitVcsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
   * the git settings
   */
  private final GitVcsSettings settings;

  /**
   * A constructor
   *
   * @param project  a context project
   * @param settings a git settings
   */
  public GitHistoryProvider(@NotNull Project project, @NotNull GitVcsSettings settings) {
    this.project = project;
    this.settings = settings;
  }

  /**
   * {@inheritDoc}
   */
  public ColumnInfo[] getRevisionColumns(final VcsHistorySession session) {
    return new ColumnInfo[0];
  }

  /**
   * {@inheritDoc}
   */
  public AnAction[] getAdditionalActions(FileHistoryPanel panel) {
    return new AnAction[0];
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
  public VcsHistorySession createSessionFor(FilePath filePath) throws VcsException {
    GitCommand command = new GitCommand(project, settings, GitUtil.getVcsRoot(project, filePath));
    List<VcsFileRevision> revisions = command.log(filePath);
    final FilePath path = filePath;

    return new VcsHistorySession(revisions) {
      @Nullable
      protected VcsRevisionNumber calcCurrentRevisionNumber() {
        GitCommand command = new GitCommand(project, settings, GitUtil.getVcsRoot(project, path));
        try {
          return command.getCurrenFileRevision(path);
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
    return new GitHistoryTreeProvider();
  }

  /**
   * {@inheritDoc}
   */
  public boolean supportsHistoryForDirectories() {
    return false;
  }
}
