// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editor;

import externalApp.ExternalAppHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * This handler is called by {@link GitRebaseEditorApp} when Git requests user input
 * via <code>GIT_EDITOR</code> or <code>GIT_REBASE_EDITOR</code>.
 */
public interface GitRebaseEditorAppHandler extends ExternalAppHandler {

  @NonNls String IJ_EDITOR_HANDLER_ENV = "INTELLIJ_REBASE_HANDER_NO";
  @NonNls String IJ_EDITOR_PORT_ENV = "INTELLIJ_REBASE_HANDER_PORT";
  @NonNls String ENTRY_POINT_NAME = "gitEditor";

  /**
   * The exit code used to indicate that editing was canceled or has failed in some other way.
   */
  int ERROR_EXIT_CODE = 2;

  /**
   * Get the answer for interactive input request from git
   *
   * @param path       Path to the output file. Handler should save user input into it.
   * @param workingDir Path to a working directory, as <code>path</code> can be relative.
   * @return Exit code
   */
  int editCommits(@NotNull String path, @NotNull String workingDir);
}
