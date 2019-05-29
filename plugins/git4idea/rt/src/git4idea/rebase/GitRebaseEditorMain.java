// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import org.apache.xmlrpc.XmlRpcClientLite;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.git4idea.GitExternalApp;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Vector;

/**
 * The rebase editor application, this editor is invoked by the git.
 * The application passes its parameter using XML RCP service
 * registered on the host passed as the first parameter. The application
 * exits with exit code returned from the service.
 */
public class GitRebaseEditorMain implements GitExternalApp {
  /**
   * The environment variable for handler no
   */
  @NonNls @NotNull public static final String IDEA_REBASE_HANDER_NO = "IDEA_REBASE_HANDER_NO";
  /**
   * The exit code used to indicate that editing was canceled or has failed in some other way.
   */
  public final static int ERROR_EXIT_CODE = 2;
  /**
   * Rebase editor handler name
   */
  @NonNls static final String HANDLER_NAME = "Git4ideaRebaseEditorHandler";
  /**
   * The prefix for cygwin files
   */
  private static final String CYGDRIVE_PREFIX = "/cygdrive/";

  /**
   * A private constructor for static class
   */
  private GitRebaseEditorMain() {
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
    String handlerId = System.getenv(IDEA_REBASE_HANDER_NO);
    if (handlerId == null) {
      System.err.println("Handler no is not specified");
      System.exit(ERROR_EXIT_CODE);
    }

    String file = args[1];
    try {
      XmlRpcClientLite client = new XmlRpcClientLite("127.0.0.1", port);
      Vector<Object> params = new Vector<>();
      params.add(handlerId);
      if (System.getProperty("os.name").toLowerCase(Locale.ENGLISH).startsWith("windows") && file.startsWith(CYGDRIVE_PREFIX)) {
        int p = CYGDRIVE_PREFIX.length();
        file = file.substring(p, p + 1) + ":" + file.substring(p + 1);
      }
      params.add(new File(file).getAbsolutePath());
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
