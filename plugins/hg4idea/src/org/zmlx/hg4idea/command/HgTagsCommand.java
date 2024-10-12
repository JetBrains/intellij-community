// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.util.Collections;
import java.util.List;

/*
 * @author bala guru
 *
 */
public class HgTagsCommand {

  private static final String TAGS_OPERATION = "tags";
  private static final List<String> ARGUMENTS = Collections.singletonList("-v");

  private final Project project;
  private final VirtualFile repo;

  public HgTagsCommand(Project project, VirtualFile repo) {
    this.project = project;
    this.repo = repo;
  }

  public HgCommandResult collectTags(){
    return new HgCommandExecutor(project).executeInCurrentThread(repo, TAGS_OPERATION, ARGUMENTS);
  }
}
