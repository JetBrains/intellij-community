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
package com.intellij.cvsSupport2.connections.ext;

import com.intellij.cvsSupport2.connections.login.CvsLoginWorkerImpl;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.cvsSupport2.javacvsImpl.io.StreamLogger;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationsListEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;

import java.io.IOException;

public class ExtLoginWorker extends CvsLoginWorkerImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.connections.ext.ExtLoginWorker");

  public ExtLoginWorker(final Project project, final CvsConnectionSettings settings, final ModalityContext executor) {
    super(project, settings, executor);
  }

  @Override
  protected void silentLoginImpl(boolean forceCheck) throws AuthenticationException {
    IConnection connection = mySettings.createConnection(new ReadWriteStatistics());
    try {
      connection.open(new StreamLogger());
    }
    finally {
      try {
        connection.close();
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
  }

  @Override
  public boolean promptForPassword() {
    if (! myExecutor.isForTemporaryConfiguration()){
      CvsRootConfiguration cvsRootConfiguration = CvsConfigurationsListEditor.reconfigureCvsRoot(mySettings.getCvsRootAsString(), null);
      if (cvsRootConfiguration == null) return false;
      return true;
    }
    else {
      return false;
    }
  }

  @Override
  protected void clearOldCredentials() {
  }
}
