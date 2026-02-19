// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import git4idea.commands.GitImplBase;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class GitSimpleEditorHandler implements GitRebaseEditorHandler {
  private static final Logger LOG = Logger.getInstance(GitSimpleEditorHandler.class);

  private final @NotNull Project myProject;

  private GitRebaseEditingResult myResult = null;

  public GitSimpleEditorHandler(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public int editCommits(@NotNull File file) {
    GitRebaseEditingResult result;
    try {
      boolean cancelled = !GitImplBase.loadFileAndShowInSimpleEditor(
        myProject,
        null,
        file,
        GitBundle.message("rebase.simple.editor.dialog.title"),
        CommonBundle.getOkButtonText()
      );
      result = cancelled ? GitRebaseEditingResult.UnstructuredEditorCancelled.INSTANCE : GitRebaseEditingResult.WasEdited.INSTANCE;
    }
    catch (Exception e) {
      LOG.error("Failed to edit git rebase file: " + file, e);
      result = new GitRebaseEditingResult.Failed(e);
    }

    myResult = result;
    return result.getExitCode();
  }

  @Override
  public @Nullable GitRebaseEditingResult getEditingResult() {
    return myResult;
  }
}
