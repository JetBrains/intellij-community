package com.intellij.cvsSupport2.connections.pserver;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.connections.ConnectionSettingsImpl;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.connection.PServerConnection;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;

/**
 * author: lesya
 */
public class PServerCvsSettings extends CvsConnectionSettings {
  private static final int DEFAULT_PORT = 2401;

  public PServerCvsSettings(CvsRootConfiguration cvsRootConfiguration) {
    super(cvsRootConfiguration);
    PORT = DEFAULT_PORT;
  }

  protected IConnection createOriginalConnection(ErrorRegistry errorRegistry,
                                                 ModalityContext executor,
                                                 CvsRootConfiguration cvsRootConfiguration) {
    if (PASSWORD == null) {
      PASSWORD = PServerLoginProvider.getInstance().getScrambledPasswordForCvsRoot(myStringRepsentation);
    }

    com.intellij.cvsSupport2.config.ProxySettings proxy_settings = cvsRootConfiguration.PROXY_SETTINGS;
    ConnectionSettings connectionSettings = new ConnectionSettingsImpl(HOST,
                                                                   PORT,
                                                                   proxy_settings.USE_PROXY,
                                                                   proxy_settings.PROXY_HOST,
                                                                   proxy_settings.PROXY_PORT,
                                                                   CvsApplicationLevelConfiguration.getInstance().TIMEOUT * 1000,
                                                                   proxy_settings.TYPE,
                                                                   proxy_settings.getLogin(),
                                                                   proxy_settings.getPassword());

    return new PServerConnection(connectionSettings, USER, PASSWORD, REPOSITORY);
  }

  public int getDefaultPort() {
    return DEFAULT_PORT;
  }

  public boolean login(ModalityContext executor) {
    return PServerLoginProvider.getInstance().login(this, executor);
  }

  public void releasePassword() {
    PASSWORD = null;
  }

  public void storePassword(String password) {
    PASSWORD = password;
  }

  public CommandException processException(CommandException t) {
    return t;
  }
}
