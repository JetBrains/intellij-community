package com.intellij.cvsSupport2.connections.ext;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsConnectionUtil;
import com.intellij.cvsSupport2.connections.ssh.SSHPasswordProviderImpl;
import com.intellij.cvsSupport2.connections.ssh.SshConnectionSettings;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.CvsBundle;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.IOCommandException;
import org.netbeans.lib.cvsclient.connection.IConnection;

import java.io.IOException;

/**
 * author: lesya
 */
public class ExtConnectionCvsSettings extends CvsConnectionSettings {
  @NonNls public static final String UNCHANDLED_RESPONSE_PREFIX = "Unhandled response: ";
  private final SshSettings mySshSettings;

  public ExtConnectionCvsSettings(CvsRootConfiguration cvsRootConfiguration) {
    super(cvsRootConfiguration);
    mySshSettings = cvsRootConfiguration.SSH_FOR_EXT_CONFIGURATION;
  }

  protected IConnection createOriginalConnection(ErrorRegistry errorRegistry, CvsRootConfiguration cvsRootConfiguration) {

    return CvsConnectionUtil.createExtConnection(this, getExtConfiguration(), mySshSettings,
                                                 SSHPasswordProviderImpl.getInstance(),
                                                 cvsRootConfiguration.PROXY_SETTINGS,
                                                 errorRegistry,
                                                 CvsApplicationLevelConfiguration.getInstance().TIMEOUT * 1000);
  }

  public int getDefaultPort() {
    return ExtConnection.DEFAULT_PORT;
  }

  public boolean login(ModalityContext executor) {
    if (!SshConnectionSettings.login(myStringRepsentation, mySshSettings)){
      return false;
    }
    return new ExtLoginProvider().login(this, executor);
  }

  public CommandException processException(CommandException t) {
    Exception sourceException = t.getUnderlyingException();
    if (!(sourceException instanceof IOException)) return t;
    String localizedMessage = t.getLocalizedMessage();
    if (!localizedMessage.startsWith(UNCHANDLED_RESPONSE_PREFIX)) return t;
    String response = localizedMessage.substring(UNCHANDLED_RESPONSE_PREFIX.length(),
                                                 localizedMessage.length() - 1);
    if (response.startsWith(USER + "@" + HOST)) {
      return new IOCommandException(new IOException(CvsBundle.message("exception.text.ext.server.rejected.access")));
    }
    else {
      return new IOCommandException(new IOException(CvsBundle.message("exception.text.cannot.establish.external.connection", response)));
    }
  }
}
