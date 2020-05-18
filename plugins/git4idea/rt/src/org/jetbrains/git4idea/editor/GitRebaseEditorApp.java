// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.git4idea.editor;

import org.apache.xmlrpc.XmlRpcClientLite;
import org.jetbrains.git4idea.GitExternalApp;

import java.io.File;
import java.util.Arrays;
import java.util.Vector;

import static org.jetbrains.git4idea.editor.GitRebaseEditorXmlRpcHandler.*;

/**
 * The rebase editor application, this editor is invoked by the git.
 * The application passes its parameter using XML RCP service
 * registered on the host passed as the first parameter. The application
 * exits with exit code returned from the service.
 */
public class GitRebaseEditorApp implements GitExternalApp {
  /**
   * A private constructor for static class
   */
  private GitRebaseEditorApp() {
  }

  /**
   * The application entry point
   *
   * @param args application arguments
   */
  @SuppressWarnings(
    {"UseOfSystemOutOrSystemErr", "HardCodedStringLiteral", "CallToPrintStackTrace", "UseOfObsoleteCollectionType"})
  public static void main(String[] args) {
    if (args.length != 2) {
      System.err.println("Invalid amount of arguments: " + Arrays.asList(args));
      System.exit(ERROR_EXIT_CODE);
    }
    int port;
    try {
      port = Integer.parseInt(args[0]);
    }
    catch (NumberFormatException ex) {
      System.err.println("Invalid port number: " + args[0]);
      System.exit(ERROR_EXIT_CODE);
      return;
    }
    String handlerId = System.getenv(IJ_EDITOR_HANDLER_ENV);
    if (handlerId == null) {
      System.err.println("Handler no is not specified");
      System.exit(ERROR_EXIT_CODE);
    }

    try {
      XmlRpcClientLite client = new XmlRpcClientLite("127.0.0.1", port);
      Vector<Object> params = new Vector<>();
      params.add(handlerId);

      params.add(args[1]);
      params.add(new File("").getAbsolutePath());

      Integer exitCode = (Integer)client.execute(HANDLER_NAME + ".editCommits", params);
      if (exitCode == null) {
        exitCode = ERROR_EXIT_CODE;
      }
      System.exit(exitCode.intValue());
    }
    catch (Exception e) {
      System.err.println("Unable to contact IDEA: " + e);
      e.printStackTrace();
      System.exit(ERROR_EXIT_CODE);
    }
  }
}
