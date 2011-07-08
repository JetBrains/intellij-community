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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.ui.ColumnInfo;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.actions.GitShowAllSubmittedFilesAction;
import git4idea.changes.GitChangeUtils;
import git4idea.config.GitExecutableValidator;
import git4idea.history.browser.SHAHash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * Git history provider implementation
 */
public class GitHistoryProvider implements VcsHistoryProvider, VcsCacheableHistorySessionFactory<Boolean, VcsAbstractHistorySession>,
                                           VcsBaseRevisionAdviser {
  /**
   * logger instance
   */
  private static final Logger log = Logger.getInstance(GitHistoryProvider.class.getName());
  /**
   * the current project instance
   */
  private final Project myProject;

  /**
   * A constructor
   *
   * @param project a context project
   */
  public GitHistoryProvider(@NotNull Project project) {
    this.myProject = project;
  }

  /**
   * {@inheritDoc}
   */
  public VcsDependentHistoryComponents getUICustomization(final VcsHistorySession session, JComponent forShortcutRegistration) {
    return VcsDependentHistoryComponents.createOnlyColumns(new ColumnInfo[0]);
  }

  /**
   * {@inheritDoc}
   * @param refresher
   */
  public AnAction[] getAdditionalActions(Runnable refresher) {
    return new AnAction[]{new GitShowAllSubmittedFilesAction(), new GitCopyHistoryRevisionNumberAction()};
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

  @Override
  public FilePath getUsedFilePath(VcsAbstractHistorySession session) {
    return null;
  }

  @Override
  public Boolean getAddinionallyCachedData(VcsAbstractHistorySession session) {
    return null;
  }

  @Override
  public VcsAbstractHistorySession createFromCachedData(Boolean aBoolean,
                                                        @NotNull List<VcsFileRevision> revisions,
                                                        @NotNull FilePath filePath,
                                                        VcsRevisionNumber currentRevision) {
    return createSession(filePath, revisions, currentRevision);
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public VcsHistorySession createSessionFor(final FilePath filePath) throws VcsException {
    List<VcsFileRevision> revisions = null;
    try {
      revisions = GitHistoryUtils.history(myProject, filePath);
    } catch (VcsException e) {
      GitVcs.getInstance(myProject).getExecutableValidator().showNotificationOrThrow(e);
    }
    return createSession(filePath, revisions, null);
  }

  private VcsAbstractHistorySession createSession(final FilePath filePath, final List<VcsFileRevision> revisions,
                                                  @Nullable final VcsRevisionNumber number) {
    return new VcsAbstractHistorySession(revisions, number) {
      @Nullable
      protected VcsRevisionNumber calcCurrentRevisionNumber() {
        try {
          return GitHistoryUtils.getCurrentRevision(myProject, GitHistoryUtils.getLastCommitName(myProject, filePath), null);
        }
        catch (VcsException e) {
          // likely the file is not under VCS anymore.
          if (log.isDebugEnabled()) {
            log.debug("Unable to retrieve the current revision number", e);
          }
          return null;
        }
      }

      public HistoryAsTreeProvider getHistoryAsTreeProvider() {
        return null;
      }

      @Override
      public VcsHistorySession copy() {
        return createSession(filePath, getRevisionList(), getCurrentRevisionNumber());
      }
    };
  }

  @Override
  public boolean getBaseVersionContent(FilePath filePath,
                                       Processor<CharSequence> processor,
                                       final String beforeVersionId,
                                       List<String> warnings)
    throws VcsException {
    if (StringUtil.isEmptyOrSpaces(beforeVersionId) || filePath.getVirtualFile() == null) return false;
    // apply if base revision id matches revision
    final VirtualFile root = GitUtil.getGitRoot(filePath);
    if (root == null) return false;

    final SHAHash shaHash = GitChangeUtils.commitExists(myProject, root, beforeVersionId, null, "--all");
    if (shaHash == null) {
      throw new VcsException("Can not apply patch to " + filePath.getPath() + ".\nCan not find revision '" + beforeVersionId + "'.");
    }

    //common parent
    final GitRevisionNumber mergeBase = GitHistoryUtils.getMergeBase(myProject, root, "HEAD", shaHash.getValue());
    if (mergeBase == null) {
      throw new VcsException("Can not apply patch to " + filePath.getPath() +
                             ".\nBase revision '" + beforeVersionId + "', used in patch, is not on current branch (reachable from current HEAD)," +
                             "\nand there is no merge base between '" + beforeVersionId + "' and HEAD.");
    }
    final ContentRevision content = GitVcs.getInstance(myProject).getDiffProvider()
      .createFileContent(new GitRevisionNumber(shaHash.getValue()), filePath.getVirtualFile());
    if (content == null) {
      throw new VcsException("Can not load content of '" + filePath.getPath() + "' for revision '" + shaHash.getValue() + "'");
    }
    final boolean matched = ! processor.process(content.getContent());
    if (shaHash.getValue().startsWith(mergeBase.getRev())) {
      // ok
      return matched;
    } else {
      if (matched) {
        warnings.add("Base revision '" + beforeVersionId + "', used in patch, is not on current branch (reachable from current HEAD)." +
                     "\nMerge base '" + mergeBase.getRev() +"' between '" + beforeVersionId + "' and HEAD was used.");
        return true;
      } else {
        throw new VcsException("Can not apply patch to " + filePath.getPath() +
                               ".\nBase revision '" + beforeVersionId + "', used in patch, is not on current branch (reachable from current HEAD)," +
                               "\nand found merge base '" + mergeBase.getRev() + "' between '" + beforeVersionId +
                               "' and HEAD doesn't match the context.");
      }
    }
  }

  public void reportAppendableHistory(final FilePath path, final VcsAppendableHistorySessionPartner partner) throws VcsException {
    final VcsAbstractHistorySession emptySession = createSession(path, Collections.<VcsFileRevision>emptyList(), null);
    partner.reportCreatedEmptySession(emptySession);
    final GitExecutableValidator validator = GitVcs.getInstance(myProject).getExecutableValidator();
    GitHistoryUtils.history(myProject, path, null, new Consumer<GitFileRevision>() {
      public void consume(GitFileRevision gitFileRevision) {
        partner.acceptRevision(gitFileRevision);
      }
    }, new Consumer<VcsException>() {
      public void consume(VcsException e) {
        if (validator.checkExecutableAndNotifyIfNeeded()) {
          partner.reportException(e);
        }
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  public boolean supportsHistoryForDirectories() {
    return true;
  }
}
