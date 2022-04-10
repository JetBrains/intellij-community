// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editor;

import externalApp.ExternalApp;
import externalApp.ExternalAppUtil;

import java.io.File;
import java.util.Arrays;

import static git4idea.editor.GitRebaseEditorXmlRpcHandler.ERROR_EXIT_CODE;
import static git4idea.editor.GitRebaseEditorXmlRpcHandler.IJ_EDITOR_HANDLER_ENV;

/**
 * The rebase editor application, this editor is invoked by the git.
 * The application passes its parameter using XML RCP service
 * registered on the host passed as the first parameter. The application
 * exits with exit code returned from the service.
 */
public class GitRebaseEditorApp implements ExternalApp {

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) {
    try {
      if (args.length != 2) {
        System.err.println("Invalid amount of arguments: " + Arrays.asList(args));
        System.exit(ERROR_EXIT_CODE);
        return;
      }

      int xmlRpcPort;
      try {
        xmlRpcPort = Integer.parseInt(args[0]);
      }
      catch (NumberFormatException ex) {
        System.err.println("Invalid port number: " + args[0]);
        System.exit(ERROR_EXIT_CODE);
        return;
      }

      String handlerNo = ExternalAppUtil.getEnv(IJ_EDITOR_HANDLER_ENV);

      Integer response = ExternalAppUtil.sendXmlRequest(GitRebaseEditorXmlRpcHandler.RPC_METHOD_NAME, xmlRpcPort,
                                                   handlerNo, args[1], new File("").getAbsolutePath());
      int exitCode = response != null ? response.intValue() : ERROR_EXIT_CODE;

      System.exit(exitCode);
    }
    catch (Throwable t) {
      System.err.println(t.getMessage());
      t.printStackTrace(System.err);
      System.exit(ERROR_EXIT_CODE);
    }
  }
}
