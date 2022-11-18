// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editor;

import externalApp.ExternalAppHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * This handler is called via XML RPC from {@link GitRebaseEditorApp} when Git requests user input
 * via <code>GIT_EDITOR</code> or <code>GIT_REBASE_EDITOR</code>.
 */
public interface GitRebaseEditorAppHandler extends ExternalAppHandler {

  @NonNls String IJ_EDITOR_HANDLER_ENV = "IDEA_REBASE_HANDER_NO";
  @NonNls String HANDLER_NAME = GitRebaseEditorAppHandler.class.getName();
  String RPC_METHOD_NAME = HANDLER_NAME + ".editCommits";

  /**
   * The exit code used to indicate that editing was canceled or has failed in some other way.
   */
  int ERROR_EXIT_CODE = 2;

  /**
   * Get the answer for interactive input request from ssh
   *
   * @param handlerNo  Handler uuid passed via {@link #IJ_EDITOR_HANDLER_ENV}
   * @param path       Path to output file. Handler should save user input into it.
   * @param workingDir Path to a working directory, as <code>path</code> can be relative.
   * @return Exit code
   */
  @SuppressWarnings("UnusedDeclaration")
  int editCommits(@NotNull String handlerNo, @NotNull String path, @NotNull String workingDir);
}
