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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.vcsUtil.VcsFileUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgEncodingUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.zmlx.hg4idea.HgErrorHandler.ensureSuccess;

public class HgCommitCommand {

  private static final String TEMP_FILE_NAME = ".hg4idea-commit.tmp";

  private final Project myProject;
  private final VirtualFile myRoot;
  private final String myMessage;
  @NotNull private final Charset myCharset;

  private Set<HgFile> myFiles = Collections.emptySet();

  public HgCommitCommand(Project project, @NotNull VirtualFile root, String message) {
    myProject = project;
    myRoot = root;
    myMessage = message;
    myCharset = HgEncodingUtil.getDefaultCharset(myProject);
  }

  public void setFiles(@NotNull Set<HgFile> files) {
    myFiles = files;
  }

  public void execute() throws HgCommandException, VcsException {
    if (StringUtil.isEmptyOrSpaces(myMessage)) {
      throw new HgCommandException(HgVcsMessages.message("hg4idea.commit.error.messageEmpty"));
    }
    //if it's merge commit, so myFiles is Empty. Need to commit all files in changeList.
    // see HgCheckinEnviroment->commit() method
    if (myFiles.isEmpty()) {
      commitChunkFiles(Collections.<String>emptyList(), false);
    }
    else {
      List<String> relativePaths = ContainerUtil.map2List(myFiles, new Function<HgFile, String>() {
        @Override
        public String fun(HgFile file) {
          return file.getRelativePath();
        }
      });
      List<List<String>> chunkedCommits = VcsFileUtil.chunkRelativePaths(relativePaths);
      int size = chunkedCommits.size();
      commitChunkFiles(chunkedCommits.get(0), false);
      HgVcs vcs = HgVcs.getInstance(myProject);
      boolean amendCommit = vcs != null && vcs.getVersion().isAmendSupported();
      for (int i = 1; i < size; i++) {
        List<String> chunk = chunkedCommits.get(i);
        commitChunkFiles(chunk, amendCommit);
      }
    }
    if (!myProject.isDisposed()) {
      HgRepositoryManager manager = HgUtil.getRepositoryManager(myProject);
      manager.updateRepository(myRoot);
    }
    final MessageBus messageBus = myProject.getMessageBus();
    messageBus.syncPublisher(HgVcs.REMOTE_TOPIC).update(myProject, null);
    messageBus.syncPublisher(HgVcs.BRANCH_TOPIC).update(myProject, null);
  }

  private void commitChunkFiles(List<String> chunk, boolean amendCommit) throws VcsException {
    List<String> parameters = new LinkedList<String>();
    parameters.add("--logfile");
    parameters.add(saveCommitMessage().getAbsolutePath());
    parameters.addAll(chunk);
    if (amendCommit) {
      parameters.add("--amend");
    }
    HgCommandExecutor executor = new HgCommandExecutor(myProject);
    executor.setCharset(myCharset);
    ensureSuccess(executor.executeInCurrentThread(myRoot, "commit", parameters));
  }

  private File saveCommitMessage() throws VcsException {
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

}
