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
package org.jetbrains.plugins.github.ui;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubSettings;

/**
 * @author Aleksey Pivovarov
 */
public class GithubBasicLoginDialog extends GithubLoginDialog {

  public GithubBasicLoginDialog(@Nullable Project project) {
    super(project);
    myGithubLoginPanel.lockAuthType(GithubAuthData.AuthType.BASIC);
  }

  @Override
  protected void saveCredentials(GithubAuthData auth) {
    final GithubSettings settings = GithubSettings.getInstance();
    if (settings.getAuthType() != GithubAuthData.AuthType.TOKEN) {
      settings.setCredentials(myGithubLoginPanel.getHost(), auth, myGithubLoginPanel.isSavePasswordSelected());
    }
  }
}
