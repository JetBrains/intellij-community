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
