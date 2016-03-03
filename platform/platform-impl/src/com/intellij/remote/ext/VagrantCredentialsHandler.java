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
package com.intellij.remote.ext;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.VagrantBasedCredentialsHolder;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VagrantCredentialsHandler extends RemoteCredentialsHandlerBase<VagrantBasedCredentialsHolder> {

  public static final String VAGRANT_PREFIX = "vagrant://";

  public VagrantCredentialsHandler(VagrantBasedCredentialsHolder credentials) {
    super(credentials);
  }

  @Override
  public String getId() {
    VagrantBasedCredentialsHolder cred = getCredentials();
    return VAGRANT_PREFIX + cred.getVagrantFolder()
           + (StringUtil.isNotEmpty(cred.getMachineName()) ?
              "@" + cred.getMachineName() : "");
  }

  @Override
  public void save(@NotNull Element rootElement) {
    getCredentials().save(rootElement);
  }

  @Override
  public String getPresentableDetails(String interpreterPath) {
    VagrantBasedCredentialsHolder cred = getCredentials();
    String pathRelativeToHome = FileUtil.getLocationRelativeToUserHome(cred.getVagrantFolder());
    return "Vagrant VM " +
           (StringUtil.isNotEmpty(cred.getMachineName()) ? "'" + cred.getMachineName() + "' " : "") +
           "at " + (pathRelativeToHome.length() < cred.getVagrantFolder().length() ? pathRelativeToHome : cred.getVagrantFolder())
           + " (" + interpreterPath + ")";
  }

  @Override
  public void load(@Nullable Element rootElement) {
    if (rootElement != null) {
      getCredentials().load(rootElement);
    }
  }
}
