package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ExtConfiguration;
import com.intellij.cvsSupport2.config.LocalSettings;
import com.intellij.cvsSupport2.config.ProxySettings;
import com.intellij.cvsSupport2.connections.ssh.ui.SshSettings;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsListenerWithProgress;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.connection.IConnection;


/**
 * author: lesya
 */
public abstract class CvsConnectionSettings extends CvsRootData implements CvsEnvironment {

  private final CvsRootConfiguration myCvsRootConfiguration;

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

  public IConnection createConnection(ReadWriteStatistics statistics, ModalityContext executor) {
    CvsListenerWithProgress cvsCommandStopper = CvsListenerWithProgress.createOnProgress();
    IConnection originalConnection = createOriginalConnection(cvsCommandStopper, executor, myCvsRootConfiguration);
    if (originalConnection instanceof SelfTestingConnection) {
      return new SelfTestingConnectionWrapper(originalConnection, statistics, cvsCommandStopper);
    }
    else {
      return new ConnectionWrapper(originalConnection, statistics, cvsCommandStopper);
    }
  }

  protected abstract IConnection createOriginalConnection(ErrorRegistry errorRegistry,
                                                          ModalityContext executor,
                                                          CvsRootConfiguration cvsRootConfiguration);

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
}
