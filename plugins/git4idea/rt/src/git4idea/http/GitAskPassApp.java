/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.http;

import externalApp.ExternalApp;
import externalApp.ExternalAppEntry;
import externalApp.ExternalAppUtil;
import externalApp.ExternalCli;

/**
 * <p>This is a program that would be called by Git when an HTTP connection is needed, that requires authorization,
 * and if {@code GIT_ASKPASS} variable is set to the script that invokes this program.</p>
 * <p>The program is called separately for each authorization aspect.
 * I. e. if no username is specified, then it is started and queried for the username, and then started once again for the password.</p>
 * <p>Since Git 1.7.9 the query format is the following:
 *    <ul>
 *      <li>{@code Username for 'https://bitbucket.org':}</li>
 *      <li>{@code Password for 'https://bitbucket.org':}</li>
 *      <li>{@code Password for 'https://username@bitbucket.org':}</li>
 *    </ul>
 * </p>
 * <p>Before Git 1.7.9 the query didn't contain the URL:
 *   <ul>
 *     <li>{@code Username: }</li>
 *     <li>{@code Password: }</li>
 *   </ul>
 * </p>
 * <p>Git expects the reply from the program's standard output.</p>
 *
 * @author Kirill Likhodedov
 */
public class GitAskPassApp implements ExternalApp, ExternalCli {

  @Override
  public int entryPoint(ExternalAppEntry entry) {
    try {
      if (entry.getArgs().length < 1) {
        throw new IllegalArgumentException("No arguments specified!");
      }

      String handlerId = ExternalAppUtil.getEnv(GitAskPassAppHandler.IJ_ASK_PASS_HANDLER_ENV, entry.getEnvironment());
      int xmlRpcPort = ExternalAppUtil.getEnvInt(GitAskPassAppHandler.IJ_ASK_PASS_PORT_ENV, entry.getEnvironment());

      String description = entry.getArgs()[0];

      ExternalAppUtil.Result result = ExternalAppUtil.sendIdeRequest(GitAskPassAppHandler.ENTRY_POINT_NAME, xmlRpcPort,
                                                                     handlerId, description);
      if (result.isError) {
        entry.getStderr().println(result.getPresentableError());
        return 1;
      }

      String ans = result.response;
      if (ans == null) {
        entry.getStderr().println("Authentication request was cancelled");
        return 0;
      }

      entry.getStdout().println(ans);
      return 0;
    }
    catch (Throwable t) {
      t.printStackTrace(entry.getStderr());
      entry.getStderr().println("Could not communicate with IDE: " + t.getMessage());
      return 1;
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) {
    var exitCode = new GitAskPassApp().entryPoint(ExternalAppEntry.fromMain(args));
    System.exit(exitCode);
  }
}
