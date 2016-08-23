/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.rebase;

import org.apache.xmlrpc.XmlRpcClientLite;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.Vector;

/**
 * The rebase editor application, this editor is invoked by the git.
 * The application passes its parameter using XML RCP service
 * registered on the host passed as the first parameter. The application
 * exits with exit code returned from the service.
 */
public class GitRebaseEditorMain {
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
    final String handlerValue = System.getenv(IDEA_REBASE_HANDER_NO);
    if (handlerValue == null) {
      System.err.println("Handler no is not specified");
      System.exit(ERROR_EXIT_CODE);
    }
    int handler;
    try {
      handler = Integer.parseInt(handlerValue);
    }
    catch (NumberFormatException ex) {
      System.err.println("Invalid handler number: " + handlerValue);
      System.exit(ERROR_EXIT_CODE);
      return;
    }
    String file = args[1];
    try {
      XmlRpcClientLite client = new XmlRpcClientLite("127.0.0.1", port);
      Vector<Object> params = new Vector<>();
      params.add(handler);
      if (System.getProperty("os.name").toLowerCase().startsWith("windows") && file.startsWith(CYGDRIVE_PREFIX)) {
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
