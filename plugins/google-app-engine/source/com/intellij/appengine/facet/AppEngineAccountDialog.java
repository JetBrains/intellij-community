// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.facet;

import com.intellij.appengine.cloud.AppEngineAuthData;
import com.intellij.appengine.cloud.AppEngineCloudConfigurable;
import com.intellij.appengine.cloud.AppEngineServerConfiguration;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.credentialStore.CredentialAttributesKt.CredentialAttributes;

public final class AppEngineAccountDialog {
  private static final String PASSWORD_KEY = "GOOGLE_APP_ENGINE_PASSWORD";

  @Nullable
  public static AppEngineAuthData createAuthData(@NotNull Project project, @NotNull AppEngineServerConfiguration configuration) {
    if (configuration.isOAuth2()) {
      return AppEngineAuthData.oauth2();
    }

    String email = configuration.getEmail();
    if (!StringUtil.isEmpty(email) && configuration.isPasswordStored()) {
      String password = getStoredPassword(email);
      if (!StringUtil.isEmpty(password)) {
        return AppEngineAuthData.login(email, password);
      }
    }

    AppEngineCloudConfigurable configurable = new AppEngineCloudConfigurable(configuration, project, false);
    boolean ok = ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
    if (!ok) {
      return null;
    }
    if (configurable.isOAuth2()) {
      return AppEngineAuthData.oauth2();
    }
    return AppEngineAuthData.login(configurable.getEmail(), configurable.getPassword());
  }

  public static void storePassword(@NotNull String email, @NotNull String password) {
    String accountName = getPasswordKey(email);
    PasswordSafe.getInstance().set(CredentialAttributes(AppEngineAccountDialog.class, accountName), new Credentials(accountName, password));
  }

  private static String getPasswordKey(String email) {
    return PASSWORD_KEY + "_" + email;
  }

  @Nullable
  private static String getStoredPassword(String email) {
    if (StringUtil.isEmpty(email)) {
      return null;
    }
    return PasswordSafe.getInstance().getPassword(CredentialAttributesKt.CredentialAttributes(AppEngineAccountDialog.class, getPasswordKey(email)));
  }
}
