/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.connections.local;

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThreeState;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.IConnection;

/**
 * author: lesya
 */
public class LocalConnectionSettings extends CvsConnectionSettings{
  public LocalConnectionSettings(String stringRepresentation, CvsRootConfiguration cvsRootConfiguration) {
    super(cvsRootConfiguration);
  }

  public int getDefaultPort() {
    return -1;
  }

  protected IConnection createOriginalConnection(ErrorRegistry errorRegistry, CvsRootConfiguration cvsRootConfiguration) {

    return new LocalConnection(REPOSITORY, getLocalConfiguration(), errorRegistry);
  }

  public CvsLoginWorker getLoginWorker(ModalityContext executor, Project project) {
    return new CvsLoginWorker() {
      public boolean promptForPassword() {
        return true;
      }

      public ThreeState silentLogin(boolean forceCheck) {
        return ThreeState.YES;
      }

      public void goOffline() {
      }
    };
  }

  public CommandException processException(CommandException t) {
    return t;
  }
}
