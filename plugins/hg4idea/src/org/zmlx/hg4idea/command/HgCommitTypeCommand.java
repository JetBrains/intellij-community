/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgEncodingUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class HgCommitTypeCommand {

  private static final String TEMP_FILE_NAME = ".hg4idea-commit.tmp";

  @NotNull protected final Project myProject;
  @NotNull protected final HgRepository myRepository;
  @NotNull private final String myMessage;
  @NotNull private final Charset myCharset;
  protected final boolean myAmend;

  private Set<HgFile> myFiles = Collections.emptySet();

  public HgCommitTypeCommand(@NotNull Project project, @NotNull HgRepository repository, @NotNull String message, boolean amend) {
    myProject = project;
    myRepository = repository;
    myMessage = message;
    myAmend = amend;
    myCharset = HgEncodingUtil.getDefaultCharset(myProject);
  }

  public void setFiles(@NotNull Set<HgFile> files) {
    myFiles = files;
  }

  protected File saveCommitMessage() throws VcsException {
    File systemDir = new File(PathManager.getSystemPath());
    File tempFile = new File(systemDir, TEMP_FILE_NAME);
    try {
      FileUtil.writeToFile(tempFile, myMessage.getBytes(myCharset));
    }
    catch (IOException e) {
      throw new VcsException("Couldn't prepare commit message", e);
    }
    return tempFile;
  }


  public void executeInCurrentThread() throws HgCommandException, VcsException {
    if (StringUtil.isEmptyOrSpaces(myMessage)) {
      throw new HgCommandException(HgVcsMessages.message("hg4idea.commit.error.messageEmpty"));
    }
    if (myFiles.isEmpty()) {
      executeChunked(Collections.emptyList());
    }
    else {
      List<String> relativePaths = ContainerUtil.map2List(myFiles, file -> file.getRelativePath());
      List<List<String>> chunkedCommits = VcsFileUtil.chunkArguments(relativePaths);
      executeChunked(chunkedCommits);
    }
    myRepository.update();
    BackgroundTaskUtil.syncPublisher(myProject, HgVcs.REMOTE_TOPIC).update(myProject, null);
  }

  protected abstract void executeChunked(@NotNull List<List<String>> chunkedCommits) throws VcsException;
}
