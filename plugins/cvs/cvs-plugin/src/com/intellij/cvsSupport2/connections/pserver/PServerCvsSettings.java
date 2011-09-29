/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.connections.pserver;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsConnectionUtil;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.openapi.project.Project;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.IConnection;

/**
 * author: lesya
 */
public class PServerCvsSettings extends CvsConnectionSettings {

  public PServerCvsSettings(CvsRootConfiguration cvsRootConfiguration) {
    super(cvsRootConfiguration);
  }

  @Override
  protected IConnection createOriginalConnection(ErrorRegistry errorRegistry, CvsRootConfiguration cvsRootConfiguration) {
    if (PASSWORD == null) {
      PASSWORD = PServerLoginProvider.getInstance().getScrambledPasswordForCvsRoot(getCvsRootAsString());
    }

    return CvsConnectionUtil.createPServerConnection(this, cvsRootConfiguration.PROXY_SETTINGS, getTimeoutMillis());
  }

  public static int getTimeoutMillis() {
    return CvsApplicationLevelConfiguration.getInstance().TIMEOUT * 1000;
  }

  @Override
  public int getDefaultPort() {
    return 2401;
  }

  @Override
  public CvsLoginWorker getLoginWorker(Project project) {
    return PServerLoginProvider.getInstance().getLoginWorker(project, this);
  }

  public void releasePassword() {
    PASSWORD = null;
  }

  public void storePassword(String password) {
    PASSWORD = password;
  }

  @Override
  public CommandException processException(CommandException t) {
    return t;
  }  
}
