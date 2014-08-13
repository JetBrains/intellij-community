/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.push.*;
import com.intellij.dvcs.push.ui.RepositoryNode;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgOutgoingCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.log.HgBaseLogParser;
import org.zmlx.hg4idea.log.HgHistoryUtil;
import org.zmlx.hg4idea.util.HgChangesetUtil;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgVersion;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HgOutgoingCommitsProvider extends OutgoingCommitsProvider {


  private static final Logger LOG = Logger.getInstance(HgOutgoingCommitsProvider.class);
  private static final String LOGIN_AND_REFRESH_LINK = "Enter Password & Refresh";

  @NotNull
  @Override
  public OutgoingResult getOutgoingCommits(@NotNull final Repository repository,
                                           @NotNull final PushSpec pushSpec,
                                           boolean initial) {
    final Project project = repository.getProject();
    HgVcs hgvcs = HgVcs.getInstance(project);
    assert hgvcs != null;
    final HgVersion version = hgvcs.getVersion();
    String[] templates = HgBaseLogParser.constructFullTemplateArgument(true, version);
    HgOutgoingCommand hgOutgoingCommand = new HgOutgoingCommand(project);
    PushTarget target = pushSpec.getTarget();
    if (target != null) {
      assert target instanceof HgTarget : String.format("Wrong Target for Repository %s", repository.getPresentableUrl());
    }
    HgTarget hgTarget = (HgTarget)target;
    List<VcsError> errors = new ArrayList<VcsError>();
    if (target == null || StringUtil.isEmptyOrSpaces(hgTarget.myTarget)) {
      errors.add(new VcsError("Hg push path could not be empty."));
      return new OutgoingResult(Collections.<VcsFullCommitDetails>emptyList(), errors);
    }
    HgCommandResult result = hgOutgoingCommand
      .execute(repository.getRoot(), HgChangesetUtil.makeTemplate(templates), pushSpec.getSource().getPresentation(),
               hgTarget.myTarget, initial);
    if (result == null) {
      errors.add(new VcsError("Couldn't execute hg outgoing command for " + repository));
      return new OutgoingResult(Collections.<VcsFullCommitDetails>emptyList(), errors);
    }
    List<String> resultErrors = result.getErrorLines();
    if (resultErrors != null && !resultErrors.isEmpty() && result.getExitValue() != 0) {
      for (String error : resultErrors) {
        if (HgErrorUtil.isAbortLine(error)) {
          if (HgErrorUtil.isAuthorizationError(error)) {
            VcsError authorizationError = new VcsError(error, LOGIN_AND_REFRESH_LINK);
            authorizationError.addClickListener(new TreeNodeLinkListener() {
                                                  @Override
                                                  public void onClick(@NotNull DefaultMutableTreeNode source) {
                                                    TreeNode parent = source.getParent();
                                                    if (parent instanceof RepositoryNode) {
                                                      //todo change value to something special, or create force refreshNode method
                                                      ((RepositoryNode)parent).fireOnChange(((RepositoryNode)parent).getValue());
                                                    }
                                                  }
                                                }
            );
            errors.add(authorizationError);
          }
          else {
            errors.add(new VcsError(error));
          }
        }
      }
      LOG.warn(resultErrors.toString());
    }
    return new OutgoingResult(HgHistoryUtil.createFullCommitsFromResult(project, repository.getRoot(), result, version, true), errors);
  }
}
