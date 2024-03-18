// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.command;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgVcs;
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

  private static final @NonNls String TEMP_FILE_NAME = ".hg4idea-commit.tmp";

  protected final @NotNull Project myProject;
  protected final @NotNull HgRepository myRepository;
  private final @NotNull String myMessage;
  private final @NotNull Charset myCharset;
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
      throw new VcsException(HgBundle.message("action.hg4idea.Commit.cant.prepare.commit.message.file"), e);
    }
    return tempFile;
  }


  public void executeInCurrentThread() throws HgCommandException, VcsException {
    if (StringUtil.isEmptyOrSpaces(myMessage)) {
      throw new HgCommandException(HgBundle.message("hg4idea.commit.error.messageEmpty"));
    }
    if (myFiles.isEmpty()) {
      executeChunked(Collections.emptyList());
    }
    else {
      List<String> relativePaths = ContainerUtil.map(myFiles, file -> file.getRelativePath());
      List<List<String>> chunkedCommits = VcsFileUtil.chunkArguments(relativePaths);
      executeChunked(chunkedCommits);
    }
    myRepository.update();
    BackgroundTaskUtil.syncPublisher(myProject, HgVcs.REMOTE_TOPIC).update(myProject, null);
  }

  protected abstract void executeChunked(@NotNull List<List<String>> chunkedCommits) throws VcsException;
}
