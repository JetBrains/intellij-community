/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.config.*;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsListenerWithProgress;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.connection.IConnection;


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

  @Override
  public RevisionOrDate getRevisionOrDate() {
    return RevisionOrDate.EMPTY;
  }

  @Override
  public String getRepository() {
    return REPOSITORY;
  }

  @Override
  public CvsRoot getCvsRoot() {
    return new CvsRoot(USER, REPOSITORY, getCvsRootAsString());
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
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

  @Override
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

  @Override
  public boolean isOffline() {
    return myOffline;
  }

  public void setOffline(final boolean offline) {
    myOffline = offline;
  }

}
