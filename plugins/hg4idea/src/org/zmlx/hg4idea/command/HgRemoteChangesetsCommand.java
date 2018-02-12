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
package org.zmlx.hg4idea.command;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgRemoteCommandExecutor;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.event.HyperlinkEvent;
import java.util.List;

/**
 * Common ancestor for HgIncomingCommand and HgOutgoingCommand - changeset commands which need connection to the server.
 * @author Kirill Likhodedov
 */
public abstract class HgRemoteChangesetsCommand extends HgChangesetsCommand {

  private static final Logger LOG = Logger.getInstance(HgRemoteChangesetsCommand.class);

  public HgRemoteChangesetsCommand(Project project, String command) {
    super(project, command);
  }

  @Override
  protected void addArguments(List<String> args) {
    args.add("--newest-first");
  }

  @Override
  protected boolean isSilentCommand() {
    return true;
  }

  @Nullable
  protected String getRepositoryUrl(VirtualFile root) {
    return HgUtil.getRepositoryDefaultPath(project, root);
  }

  @Override
  protected HgCommandResult executeCommandInCurrentThread(VirtualFile repo, List<String> args) {
    String repositoryURL = getRepositoryUrl(repo);
    if (repositoryURL == null) {
      LOG.info("executeCommand no default path configured");
      return null;
    }
    HgRemoteCommandExecutor executor = new HgRemoteCommandExecutor(project, repositoryURL);
    HgCommandResult result = executor.executeInCurrentThread(repo, command, args);
    if (result == HgCommandResult.CANCELLED || HgErrorUtil.isAuthorizationError(result)) {
      final HgVcs vcs = HgVcs.getInstance(project);
      if (vcs == null) {
        return result;
      }
      new HgCommandResultNotifier(project).notifyError(result, "Checking for incoming/outgoing changes disabled",
                                                       "Authentication is required to check incoming/outgoing changes in " + repositoryURL +
                                                       "<br/>You may enable checking for changes <a href='#'>in the Settings</a>.",
                                                       new NotificationListener() {
                                                         @Override
                                                         public void hyperlinkUpdate(@NotNull Notification notification,
                                                                                     @NotNull HyperlinkEvent event) {
                                                           ShowSettingsUtil.getInstance()
                                                             .showSettingsDialog(project, vcs.getConfigurable().getDisplayName());
                                                         }
                                                       });
      final HgProjectSettings projectSettings = vcs.getProjectSettings();
      projectSettings.setCheckIncomingOutgoing(false);
      BackgroundTaskUtil.syncPublisher(project, HgVcs.INCOMING_OUTGOING_CHECK_TOPIC).hide();
    }
    return result;
  }

}
