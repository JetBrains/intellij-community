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

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialPromptDialog;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.project.Project;
import com.intellij.util.PathUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.credentialStore.CredentialAttributesKt.generateServiceName;

/**
 * Swing GUI handler for the SSH events
 */
public class GitSSHGUIHandler {
  @Nullable
  static String askPassphrase(@Nullable Project project,
                              @NotNull String keyPath,
                              boolean resetPassword,
                              @NotNull GitAuthenticationMode authenticationMode,
                              @Nullable String lastError) {
    if (authenticationMode == GitAuthenticationMode.NONE) return null;
    CredentialAttributes newAttributes = passphraseCredentialAttributes(keyPath);
    Credentials credentials = PasswordSafe.getInstance().get(newAttributes);
    if (credentials != null && !resetPassword) {
      String password = credentials.getPasswordAsString();
      if (password != null && !password.isEmpty()) return password;
    }
    if (authenticationMode == GitAuthenticationMode.SILENT) return null;
    return CredentialPromptDialog.askPassword(project, GitBundle.getString("ssh.ask.passphrase.title"),
                                              GitBundle.message("ssh.ask.passphrase.message", PathUtil.getFileName(keyPath)),
                                              newAttributes, true, lastError);
  }

  @NotNull
  private static CredentialAttributes passphraseCredentialAttributes(@NotNull @NonNls String key) {
    return new CredentialAttributes(generateServiceName("Git SSH Passphrase", key), key);
  }

  @NotNull
  private static CredentialAttributes passwordCredentialAttributes(@NotNull @NonNls String key) {
    return new CredentialAttributes(generateServiceName("Git SSH Password", key), key);
  }

  @Nullable
  static String askPassword(@Nullable Project project,
                            @NotNull String username,
                            boolean resetPassword,
                            @NotNull GitAuthenticationMode authenticationMode,
                            @Nullable String error) {
    if (authenticationMode == GitAuthenticationMode.NONE) return null;
    CredentialAttributes newAttributes = passwordCredentialAttributes(username);
    Credentials credentials = PasswordSafe.getInstance().get(newAttributes);
    if (credentials != null && !resetPassword) {
      String password = credentials.getPasswordAsString();
      if (password != null) return password;
    }
    if (authenticationMode == GitAuthenticationMode.SILENT) return null;
    return CredentialPromptDialog.askPassword(project,
                                              GitBundle.getString("ssh.password.title"),
                                              GitBundle.message("ssh.password.message", username),
                                              newAttributes,
                                              true,
                                              error);
  }
}
