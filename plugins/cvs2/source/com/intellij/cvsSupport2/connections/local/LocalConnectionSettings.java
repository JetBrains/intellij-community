package com.intellij.cvsSupport2.connections.local;

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
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

  protected IConnection createOriginalConnection(ErrorRegistry errorRegistry,
                                                 ModalityContext executor,
                                                 CvsRootConfiguration cvsRootConfiguration) {

    return new LocalConnection(REPOSITORY, getLocalConfiguration(), errorRegistry, executor);
  }

  public boolean login(ModalityContext executor) {
    return true;
  }

  public CommandException processException(CommandException t) {
    return t;
  }
}
