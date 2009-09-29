package com.intellij.cvsSupport2.connections.ext;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsConnectionUtil;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.cvsSupport2.connections.ssh.SSHPasswordProviderImpl;
import com.intellij.cvsSupport2.connections.ssh.SshConnectionUtil;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.CvsBundle;
import com.intellij.util.ThreeState;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.project.Project;
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

  public CvsLoginWorker getLoginWorker(ModalityContext executor, Project project) {
    return new MyLoginWorker(project, this, executor);
  }

  private class MyLoginWorker implements CvsLoginWorker {
    private final CvsLoginWorker myWorker;
    private boolean mySshChecked;
    private final Project myProject;

    private MyLoginWorker(final Project project, final ExtConnectionCvsSettings settings, final ModalityContext executor) {
      myProject = project;
      myWorker = new ExtLoginWorker(project, settings, executor);
    }

    // todo check!!!
    public boolean promptForPassword() {
      if (! mySshChecked) {
        mySshChecked = true;
        return SshConnectionUtil.promptForPassword(mySshSettings, myStringRepsentation);
      }
      return myWorker.promptForPassword();
    }

    public ThreeState silentLogin(boolean forceCheck) {
      return myWorker.silentLogin(forceCheck);
    }

    public void goOffline() {
      myWorker.goOffline();
    }
  }

  public CommandException processException(CommandException t) {
    Exception sourceException = t.getUnderlyingException();
    if (!(sourceException instanceof IOException)) return t;
    String localizedMessage = t.getLocalizedMessage();
    if (!localizedMessage.startsWith(UNCHANDLED_RESPONSE_PREFIX)) return t;
    String response = localizedMessage.substring(UNCHANDLED_RESPONSE_PREFIX.length(),
                                                 localizedMessage.length() - 1);
    if (StringUtil.startsWithConcatenationOf(response, USER + "@", HOST)) {
      return new IOCommandException(new IOException(CvsBundle.message("exception.text.ext.server.rejected.access")));
    }
    else {
      return new IOCommandException(new IOException(CvsBundle.message("exception.text.cannot.establish.external.connection", response)));
    }
  }
}
