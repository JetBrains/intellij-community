// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editor;

import externalApp.ExternalApp;
import externalApp.ExternalAppUtil;

import java.io.File;
import java.util.Arrays;

import static git4idea.editor.GitRebaseEditorAppHandler.ERROR_EXIT_CODE;

/**
 * The rebase editor application, this editor is invoked by the git.
 */
public class GitRebaseEditorApp implements ExternalApp {

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) {
    try {
      if (args.length != 1) {
        System.err.println("Invalid arguments: " + Arrays.asList(args));
        System.exit(ERROR_EXIT_CODE);
        return;
      }

      String handlerId = ExternalAppUtil.getEnv(GitRebaseEditorAppHandler.IJ_EDITOR_HANDLER_ENV);
      int idePort = ExternalAppUtil.getEnvInt(GitRebaseEditorAppHandler.IJ_EDITOR_PORT_ENV);

      String workingDir = new File("").getAbsolutePath();
      String path = args[0];
      String bodyContent = path + "\n" + workingDir;

      ExternalAppUtil.Result result = ExternalAppUtil.sendIdeRequest(GitRebaseEditorAppHandler.ENTRY_POINT_NAME, idePort,
                                                                     handlerId, bodyContent);

      if (result.isError) {
        System.err.println(result.error);
        System.exit(ERROR_EXIT_CODE);
      }

      String response = result.response;
      if (response == null) {
        System.exit(ERROR_EXIT_CODE); // dialog cancelled
      }

      int exitCode = Integer.parseInt(response);
      System.exit(exitCode);
    }
    catch (Throwable t) {
      System.err.println(t.getMessage());
      t.printStackTrace(System.err);
      System.exit(ERROR_EXIT_CODE);
    }
  }
}
