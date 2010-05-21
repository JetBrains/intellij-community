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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.HgVcs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class HgCommitCommand {

  private static final Logger LOG = Logger.getInstance(HgCommitCommand.class.getName());

  private static final String TEMP_FILE_NAME = ".hg4idea-commit.tmp";

  private final Project project;
  private final VirtualFile repo;
  private final String message;

  private List<HgFile> files = Collections.emptyList();

  public HgCommitCommand(Project project, @NotNull VirtualFile repo, String message) {
    this.project = project;
    this.repo = repo;
    this.message = message;
  }

  public void setFiles(@NotNull List<HgFile> files) {
    this.files = files;
  }

  public void execute() throws HgCommandException {
    if (StringUtils.isBlank(message)) {
      throw new HgCommandException(HgVcsMessages.message("hg4idea.commit.error.messageEmpty"));
    }
    try {
      List<String> parameters = new LinkedList<String>();
      parameters.add("--logfile");
      parameters.add(saveCommitMessage().getAbsolutePath());
      for (HgFile hgFile : files) {
        parameters.add(hgFile.getRelativePath());
      }
      HgCommandService.getInstance(project).execute(repo, "commit", parameters);
      project.getMessageBus().syncPublisher(HgVcs.OUTGOING_TOPIC).update(project);
    } catch (IOException e) {
      LOG.error(e);
    }
  }

  private File saveCommitMessage() throws IOException {
    File systemDir = new File(PathManager.getSystemPath());
    File tempFile = new File(systemDir, TEMP_FILE_NAME);
    Writer output = new BufferedWriter(new FileWriter(tempFile, false));
    try {
      output.write(message);
    } finally {
      output.close();
    }
    return tempFile;
  }

}
