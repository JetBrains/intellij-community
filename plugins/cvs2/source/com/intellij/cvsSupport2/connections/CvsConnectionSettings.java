package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.config.*;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsListenerWithProgress;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.CommonBundle;
import com.intellij.CvsBundle;
import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;


/**
 * author: lesya
 */
public abstract class CvsConnectionSettings extends CvsRootData implements CvsEnvironment, CvsSettings {

  private final CvsRootConfiguration myCvsRootConfiguration;
  private boolean myOffline;

  protected CvsConnectionSettings(CvsRootConfiguration cvsRootConfiguration) {
    super(cvsRootConfiguration.getCvsRootAsString());
    PORT = getDefaultPort();
    myCvsRootConfiguration = cvsRootConfiguration;
  }

  public abstract int getDefaultPort();

  public RevisionOrDate getRevisionOrDate() {
    return RevisionOrDate.EMPTY;
  }

  public String getRepository() {
    return REPOSITORY;
  }

  public CvsRoot getCvsRoot() {
    return new CvsRoot(USER, REPOSITORY, getCvsRootAsString());
  }

  public boolean isValid() {
    return true;
  }

  public IConnection createConnection(ReadWriteStatistics statistics) {
    CvsListenerWithProgress cvsCommandStopper = CvsListenerWithProgress.createOnProgress();
    IConnection originalConnection = createOriginalConnection(cvsCommandStopper, myCvsRootConfiguration);
    if (originalConnection instanceof SelfTestingConnection) {
      return new SelfTestingConnectionWrapper(originalConnection, statistics, cvsCommandStopper);
    }
    else {
      return new ConnectionWrapper(originalConnection, statistics, cvsCommandStopper);
    }
  }

  protected abstract IConnection createOriginalConnection(ErrorRegistry errorRegistry, CvsRootConfiguration cvsRootConfiguration);

  protected ExtConfiguration getExtConfiguration() {
    return myCvsRootConfiguration.EXT_CONFIGURATION;
  }

  protected LocalSettings getLocalConfiguration() {
    return myCvsRootConfiguration.LOCAL_CONFIGURATION;
  }

  protected SshSettings getSshConfiguration() {
    return myCvsRootConfiguration.SSH_CONFIGURATION;
  }

  public ProxySettings getProxySettings(){
    return myCvsRootConfiguration.PROXY_SETTINGS;
  }
  
  public void setUseProxy(String proxyHost, String proxyPort) {
    super.setUseProxy(proxyHost, proxyPort);
    final ProxySettings settings = myCvsRootConfiguration.PROXY_SETTINGS;
    settings.PROXY_HOST = proxyHost;
    try {
      settings.PROXY_PORT = Integer.parseInt(proxyPort);
    }
    catch (NumberFormatException e) {
      //ignore
    }
    settings.USE_PROXY = true;
  }

  public boolean isOffline() {
    return myOffline;
  }

  public void setOffline(final boolean offline) {
    myOffline = offline;
  }

  public void showConnectionErrorMessage(final String message, final String title, final boolean suggestOffline) {
    Runnable showErrorAction = new Runnable() {
      public void run() {
        if (suggestOffline) {
          int rc = Messages.showDialog(message, title, new String[]{CommonBundle.getOkButtonText(), "Work Offline"}, 0, Messages.getErrorIcon());
          if (rc == 1) {
            setOffline(true);
          }
        }
        else {
          Messages.showErrorDialog(message, title);
        }
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      showErrorAction.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(showErrorAction);
    }
  }

  public boolean checkReportOfflineException(final AuthenticationException e) {
    if (isOffline()) return true;
    Throwable cause = e.getCause();
    if (cause instanceof SocketTimeoutException) {
      showConnectionErrorMessage(CvsBundle.message("error.message.timeout.error"),
                                 CvsBundle.message("error.dialog.title.timeout.error"),
                                 true);
      return true;
    }
    else if (cause instanceof UnknownHostException) {
      showConnectionErrorMessage(CvsBundle.message("error.message.unknown.host", HOST),
                                 CvsBundle.message("error.title.inknown.host"),
                                 true);
      return true;
    }
    else if (cause instanceof ConnectException || cause instanceof NoRouteToHostException) {
      showConnectionErrorMessage(CvsBundle.message("error.message.connection.error", HOST),
                                 CvsBundle.message("error.title.connection.error"),
                                 true);
      return true;
    }
    return false;
  }
}
