// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editor;

import externalApp.ExternalApp;
import externalApp.ExternalAppEntry;
import externalApp.ExternalAppUtil;
import externalApp.ExternalCli;

import java.util.Arrays;

import static git4idea.editor.GitRebaseEditorAppHandler.ERROR_EXIT_CODE;

/**
 * The rebase editor application, this editor is invoked by the git.
 */
public class GitRebaseEditorApp implements ExternalApp, ExternalCli {

  @Override
  public int entryPoint(ExternalAppEntry entry) {
    try {
      if (entry.getArgs().length != 1) {
        entry.getStderr().println("Invalid arguments: " + Arrays.asList(entry.getArgs()));
        return ERROR_EXIT_CODE;
      }

      String handlerId = ExternalAppUtil.getEnv(GitRebaseEditorAppHandler.IJ_EDITOR_HANDLER_ENV, entry.getEnvironment());
      int idePort = ExternalAppUtil.getEnvInt(GitRebaseEditorAppHandler.IJ_EDITOR_PORT_ENV, entry.getEnvironment());

      String workingDir = entry.getWorkingDirectory();
      String path = entry.getArgs()[0];
      String bodyContent = path + "\n" + workingDir;

      ExternalAppUtil.Result result = ExternalAppUtil.sendIdeRequest(GitRebaseEditorAppHandler.ENTRY_POINT_NAME, idePort,
                                                                     handlerId, bodyContent);

      if (result.isError) {
        entry.getStderr().println(result.getPresentableError());
        return ERROR_EXIT_CODE;
      }

      String response = result.response;
      if (response == null) {
        return ERROR_EXIT_CODE; // dialog cancelled
      }

      int exitCode = Integer.parseInt(response);
      return exitCode;
    }
    catch (Throwable t) {
      entry.getStderr().println(t.getMessage());
      t.printStackTrace(entry.getStderr());
      return ERROR_EXIT_CODE;
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) {
    var exitCode = new GitRebaseEditorApp().entryPoint(ExternalAppEntry.fromMain(args));
    System.exit(exitCode);
  }

}
