package com.intellij.cvsSupport2.connections.ext;

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ExtConfiguration;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.ssh.SshConnectionSettings;
import com.intellij.cvsSupport2.connections.ssh.ui.SshSettings;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.IOCommandException;
import org.netbeans.lib.cvsclient.connection.IConnection;

import java.io.IOException;

/**
 * author: lesya
 */
public class ExtConnectionCvsSettings extends CvsConnectionSettings {
  public static final String UNCHANDLED_RESPONSE_PREFIX = "Unhandled response: ";
  private final SshSettings mySshSettings;

  public ExtConnectionCvsSettings(CvsRootConfiguration cvsRootConfiguration) {
    super(cvsRootConfiguration);
    mySshSettings = cvsRootConfiguration.SSH_FOR_EXT_CONFIGURATION;
  }

  protected IConnection createOriginalConnection(ErrorRegistry errorRegistry,
                                                 ModalityContext executor,
                                                 CvsRootConfiguration cvsRootConfiguration) {

    ExtConfiguration extConfiguration = getExtConfiguration();
    if (extConfiguration.USE_INTERNAL_SSH_IMPLEMENTATION) {
      return SshConnectionSettings.createSshConnection(cvsRootConfiguration, this, cvsRootConfiguration.SSH_FOR_EXT_CONFIGURATION);
    }
    else {
      return new ExtConnection(HOST, USER, REPOSITORY, extConfiguration, errorRegistry, executor);
    }
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
      return new IOCommandException(new IOException("Server rejected access"));
    }
    else {
      return new IOCommandException(new IOException("Cannot establish external connection. Response from server was: " + response));
    }
  }
}
