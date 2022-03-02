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
import externalApp.ExternalAppUtil;

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
public class GitAskPassApp implements ExternalApp {

  // STDOUT is used to provide credentials to Git process; STDERR is used to print error message to the main IDEA command line.
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) {
    try {
      if (args.length < 1) {
        throw new IllegalArgumentException("No arguments specified!");
      }

      String handlerNo = ExternalAppUtil.getEnv(GitAskPassXmlRpcHandler.IJ_ASK_PASS_HANDLER_ENV);
      int xmlRpcPort = ExternalAppUtil.getEnvInt(GitAskPassXmlRpcHandler.IJ_ASK_PASS_PORT_ENV);

      String ans = ExternalAppUtil.sendXmlRequest(GitAskPassXmlRpcHandler.RPC_METHOD_NAME, xmlRpcPort,
                                             handlerNo, args[0]);
      System.out.println(ans);
    }
    catch (Throwable t) {
      System.err.println(t.getMessage());
      t.printStackTrace(System.err);
      System.exit(1);
    }
  }
}
