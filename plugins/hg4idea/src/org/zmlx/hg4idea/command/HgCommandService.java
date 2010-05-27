// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.command;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgGlobalSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public final class HgCommandService {

  static final Logger LOG = Logger.getInstance(HgCommandService.class.getName());

  private final Project project;
  private final HgGlobalSettings settings;

  private HgVcs hgVcs;

  public HgCommandService(Project project, HgGlobalSettings settings) {
    this.project = project;
    this.settings = settings;
  }

  public static HgCommandService getInstance(Project project) {
    return project.getComponent(HgCommandService.class);
  }

  HgCommandResult execute(@NotNull VirtualFile repo, String operation, List<String> arguments) {
    return execute(
      repo, Collections.<String>emptyList(), operation, arguments, Charset.defaultCharset()
    );
  }

  HgCommandResult execute(@NotNull VirtualFile repo, List<String> config,
    String operation, List<String> arguments) {
    return execute(repo, config, operation, arguments, Charset.defaultCharset());
  }

  HgCommandResult execute(@NotNull VirtualFile repo, List<String> config,
    String operation, List<String> arguments, Charset charset) {
    
    if (hgVcs == null) {
      hgVcs = HgVcs.getInstance(project);
    }

    if (hgVcs == null || !hgVcs.validateHgExecutable()) {
      return HgCommandResult.EMPTY;
    }

    List<String> cmdLine = new LinkedList<String>();
    cmdLine.add(settings.getHgExecutable());
    if (config != null && !config.isEmpty()) {
      cmdLine.add("--config");
      cmdLine.addAll(config);
    }
    cmdLine.add(operation);
    if (arguments != null && arguments.size() != 0) {
      cmdLine.addAll(arguments);
    }
    ShellCommand shellCommand = new ShellCommand();
    try {
      LOG.info(cmdLine.toString());
      return shellCommand.execute(cmdLine, repo.getPath(), charset);
    } catch (ShellCommandException e) {
      showError(e);
      LOG.error(e.getMessage(), e);
    }
    return HgCommandResult.EMPTY;
  }

  private void showError(Exception e) {
    StringBuilder message = new StringBuilder();
    message.append(HgVcsMessages.message("hg4idea.command.executable.error",
      settings.getHgExecutable()))
      .append("\n")
      .append("Original Error:\n")
      .append(e.getMessage());

    VcsUtil.showErrorMessage(
      project,
      message.toString(),
      HgVcsMessages.message("hg4idea.error")
    );
  }

}
