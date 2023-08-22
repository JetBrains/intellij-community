// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote.ext;

import com.intellij.ide.IdeBundle;
import com.intellij.remote.WebDeploymentCredentialsHolder;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WebDeploymentCredentialsHandler extends RemoteCredentialsHandlerBase<WebDeploymentCredentialsHolder> {

  public WebDeploymentCredentialsHandler(WebDeploymentCredentialsHolder credentials) {
    super(credentials);
  }

  @Override
  public @NotNull String getId() {
    return getCredentials().getCredentialsId();
  }

  @Override
  public void save(@NotNull Element rootElement) {
    getCredentials().save(rootElement);
  }

  @Override
  public String getPresentableDetails(String interpreterPath) {
    return "(" + getCredentials().getCredentialsId() + interpreterPath + ")";
  }

  @Override
  public void load(@Nullable Element rootElement) {
    WebDeploymentCredentialsHolder credentials = getCredentials();
    if (rootElement != null) {
      credentials.load(rootElement);
    }
    else {
      credentials.setWebServerConfigId("");
      credentials.setWebServerConfigName(IdeBundle.message("name.invalid"));
    }
  }
}
