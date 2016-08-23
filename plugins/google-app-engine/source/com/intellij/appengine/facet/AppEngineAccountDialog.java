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
package com.intellij.appengine.facet;

import com.intellij.appengine.cloud.AppEngineAuthData;
import com.intellij.appengine.cloud.AppEngineCloudConfigurable;
import com.intellij.appengine.cloud.AppEngineServerConfiguration;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class AppEngineAccountDialog {
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
    PasswordSafe.getInstance().setPassword(AppEngineAccountDialog.class, getPasswordKey(email), password);
  }

  private static String getPasswordKey(String email) {
    return PASSWORD_KEY + "_" + email;
  }

  @Nullable
  private static String getStoredPassword(String email) {
    if (StringUtil.isEmpty(email)) {
      return null;
    }
    return PasswordSafe.getInstance().getPassword(AppEngineAccountDialog.class, getPasswordKey(email));
  }
}
