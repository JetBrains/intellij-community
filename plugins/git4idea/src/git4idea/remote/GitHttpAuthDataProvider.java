/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.remote;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.AuthData;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides authentication information to the {@link git4idea.commands.GitHttpAuthenticator} on attempt to connect an HTTP remote.
 * Useful for reusing Github credentials stored in the settings to connect the github remote (IDEA-87530).
 * <p>
 * {@link AuthData} with null password will be ignored
 */
public interface GitHttpAuthDataProvider {

  ExtensionPointName<GitHttpAuthDataProvider> EP_NAME = ExtensionPointName.create("Git4Idea.GitHttpAuthDataProvider");

  @RequiresBackgroundThread
  default @Nullable AuthData getAuthData(@NotNull Project project, @NotNull String url, @NotNull String login) {
    return getAuthData(project, url);
  }

  @RequiresBackgroundThread
  default @Nullable AuthData getAuthData(@NotNull Project project, @NotNull String url) {
    return null;
  }

  @RequiresBackgroundThread
  default void forgetPassword(@NotNull Project project, @NotNull String url, @NotNull AuthData authData) {
  }

  /**
   * @return true  - if provider does not show any prompts except internal password storage access {@link com.intellij.ide.passwordSafe.PasswordSafe},
   * such provider can be interrogated by GitHttpAuthenticator with {@link git4idea.commands.GitAuthenticationMode#SILENT} mode
   */
  default boolean isSilent() {return false;}
}
