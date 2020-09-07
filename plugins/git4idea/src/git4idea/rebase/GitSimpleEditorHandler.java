// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import git4idea.commands.GitImplBase;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class GitSimpleEditorHandler implements GitRebaseEditorHandler {
  private static final Logger LOG = Logger.getInstance(GitSimpleEditorHandler.class);

  @NotNull private final Project myProject;

  public GitSimpleEditorHandler(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public int editCommits(@NotNull File file) {
    try {
      boolean cancelled = !GitImplBase.loadFileAndShowInSimpleEditor(
        myProject,
        null,
        file,
        GitBundle.message("rebase.simple.editor.dialog.title"),
        CommonBundle.getOkButtonText()
      );
      return cancelled ? ERROR_EXIT_CODE : 0;
    }
    catch (Exception e) {
      LOG.error("Failed to edit git rebase file: " + file, e);
      return ERROR_EXIT_CODE;
    }
  }

  @Override
  public boolean wasCommitListEditorCancelled() {
    return false;
  }

  @Override
  public boolean wasUnstructuredEditorCancelled() {
    return false;
  }
}
