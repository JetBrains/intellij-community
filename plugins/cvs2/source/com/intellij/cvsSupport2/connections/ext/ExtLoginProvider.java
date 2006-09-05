package com.intellij.cvsSupport2.connections.ext;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ui.CvsConfigurationsListEditor;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.cvsSupport2.javacvsImpl.io.StreamLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;

import java.io.IOException;

/**
 * author: lesya
 */
public class ExtLoginProvider {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.connections.ext.ExtLoginProvider");

  public boolean login(CvsConnectionSettings env, ModalityContext executor) {

    IConnection connection = env.createConnection(new ReadWriteStatistics());
    try {
      connection.open(new StreamLogger());
      return true;
    }
    catch(AuthenticationException ex) {
      //noinspection SimplifiableIfStatement
      if (env.checkReportOfflineException(ex)) {
        return false;
      }
      return relogin(ex, env, executor);
    }
    catch (Exception ex) {
      return relogin(ex, env, executor);
    }
    finally {
      try {
        connection.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

  }

  private boolean relogin(Exception ex, CvsConnectionSettings env, ModalityContext executor) {
    return relogin(ex.getLocalizedMessage(), env, executor);
  }

  private boolean relogin(String message, CvsConnectionSettings env, ModalityContext executor) {
    Messages.showMessageDialog(message, CvsBundle.message("message.error.cannot.connect.to.cvs.title"), Messages.getErrorIcon());
    if (!executor.isForTemporaryConfiguration()){
      CvsRootConfiguration cvsRootConfiguration = CvsConfigurationsListEditor.reconfigureCvsRoot(env.getCvsRootAsString(), null);
      if (cvsRootConfiguration == null) return false;
      return login(env, executor);
    }
    else {
      return false;
    }
  }

}
