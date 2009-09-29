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
