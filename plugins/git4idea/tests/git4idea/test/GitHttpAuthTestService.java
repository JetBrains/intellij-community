/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.test;

import com.intellij.externalProcessAuthHelper.AuthenticationGate;
import com.intellij.externalProcessAuthHelper.AuthenticationMode;
import com.intellij.openapi.project.Project;
import git4idea.commands.GitHttpAuthService;
import git4idea.commands.GitHttpAuthenticator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

public class GitHttpAuthTestService extends GitHttpAuthService {

  @NotNull private GitHttpAuthenticator myAuthenticator = STUB_AUTHENTICATOR;

  @NotNull
  @Override
  public GitHttpAuthenticator createAuthenticator(@NotNull Project project,
                                                  @NotNull Collection<String> urls,
                                                  @NotNull File workingDirectory,
                                                  @NotNull AuthenticationGate authenticationGate,
                                                  @NotNull AuthenticationMode authenticationMode) {
    return myAuthenticator;
  }

  public void register(@NotNull GitHttpAuthenticator authenticator) {
    myAuthenticator = authenticator;
  }

  public void cleanup() {
    myAuthenticator = STUB_AUTHENTICATOR;
  }

  /**
   * NOOP handler providing empty values for credentials
   */
  public static final GitHttpAuthenticator STUB_AUTHENTICATOR = new GitHttpAuthenticator() {
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
