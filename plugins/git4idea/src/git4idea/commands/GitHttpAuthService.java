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
package git4idea.commands;

import com.intellij.externalProcessAuthHelper.GitAuthenticationGate;
import com.intellij.externalProcessAuthHelper.GitAuthenticationMode;
import com.intellij.externalProcessAuthHelper.GitXmlRpcHandlerService;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import git4idea.http.GitAskPassApp;
import git4idea.http.GitAskPassXmlRpcHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.UUID;

/**
 * Provides the authentication mechanism for Git HTTP connections.
 */
public abstract class GitHttpAuthService extends GitXmlRpcHandlerService<GitHttpAuthenticator> {

  protected GitHttpAuthService() {
    super("intellij-git-askpass", GitAskPassXmlRpcHandler.HANDLER_NAME, GitAskPassApp.class);
  }

  @NotNull
  @Override
  protected Object createRpcRequestHandlerDelegate() {
    return new InternalRequestHandlerDelegate();
  }

  /**
   * Creates new {@link GitHttpAuthenticator} that will be requested to handle username and password requests from Git.
   */
  @NotNull
  public abstract GitHttpAuthenticator createAuthenticator(@NotNull Project project,
                                                           @NotNull Collection<String> urls,
                                                           @NotNull File workingDirectory,
                                                           @NotNull GitAuthenticationGate authenticationGate,
                                                           @NotNull GitAuthenticationMode authenticationMode);

  /**
   * Internal handler implementation class, it is made public to be accessible via XML RPC.
   */
  public class InternalRequestHandlerDelegate implements GitAskPassXmlRpcHandler {
    @Override
    public @NotNull String handleInput(@NotNull String handlerNo, @NotNull String arg) {
      GitHttpAuthenticator handler = getHandler(UUID.fromString(handlerNo));

      boolean usernameNeeded = StringUtilRt.startsWithIgnoreCase(arg, "username"); //NON-NLS

      String[] split = arg.split(" ");
      String url = split.length > 2 ? parseUrl(split[2]) : "";

      return getDefaultValueIfCancelled(() -> {
        return usernameNeeded ? handler.askUsername(url) : handler.askPassword(url);
      }, "");
    }
  }

  private static String parseUrl(@NotNull String url) {
    // un-quote and remove the trailing colon
    url = StringUtil.trimStart(url, "'");
    url = StringUtil.trimEnd(url, ":");
    url = StringUtil.trimEnd(url, "'");
    return url;
  }

  @NotNull
  public static <T> T getDefaultValueIfCancelled(@NotNull Computable<? extends T> operation, @NotNull T defaultValue) {
    try {
      return operation.compute();
    }
    catch (ProcessCanceledException pce) {
      return defaultValue;
    }
  }

  /**
   * NOOP handler providing empty values for credentials
   */
  protected static final GitHttpAuthenticator STUB_AUTHENTICATOR = new GitHttpAuthenticator() {
    @NotNull
    @Override
    public String askPassword(@NotNull String url) {
      return "";
    }

    @NotNull
    @Override
    public String askUsername(@NotNull String url) {
      return "";
    }

    @Override
    public void saveAuthData() {
    }

    @Override
    public void forgetPassword() {
    }

    @Override
    public boolean wasCancelled() {
      return false;
    }

    @Override
    public boolean wasRequested() {
      return false;
    }
  };
}
