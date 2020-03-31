// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.connections;

import com.intellij.openapi.util.Comparing;
import java.util.Objects;

/**
 * author: lesya
 */
public class CvsRootData implements CvsSettings{
  private final String myStringRepresentation;
  public CvsMethod METHOD = null;
  public String HOST = "";
  public String USER = "";
  public String REPOSITORY = "";
  public int PORT;
  public String PASSWORD = "";
  public String PROXY_HOST = null;
  public String PROXY_PORT = null;
  public boolean CONTAINS_PROXY_INFO = false;

  public CvsRootData(String stringRepresentation) {
    myStringRepresentation = stringRepresentation;
  }

  public String getCvsRootAsString() {
    return myStringRepresentation;
  }

  public boolean equals(Object object) {
    if (!(object instanceof CvsRootData)) return false;
    CvsRootData other = (CvsRootData)object;

    return Comparing.equal(METHOD, other.METHOD)
           && Objects.equals(HOST, other.HOST)
           && Objects.equals(USER, other.USER)
           && Objects.equals(REPOSITORY, other.REPOSITORY)
           && PORT == other.PORT;
  }

  public int hashCode() {
    int methodHashCode = METHOD == null ? 0 : METHOD.hashCode();
    return methodHashCode ^ HOST.hashCode() ^ USER.hashCode() ^ REPOSITORY.hashCode() ^ PORT;
  }

  @Override
  public void setPassword(String password) {
    PASSWORD = password;
  }

  @Override
  public void setHost(String host) {
    HOST = host;
  }

  @Override
  public void setPort(int port) {
    PORT = port;
  }

  @Override
  public void setUser(String user) {
    USER = user;
  }

  @Override
  public void setRepository(String repository) {
    REPOSITORY = repository;
  }

  @Override
  public void setUseProxy(String proxyHost, String proxyPort) {
    CONTAINS_PROXY_INFO = true;
    PROXY_HOST = proxyHost;
    PROXY_PORT = proxyPort;
  }
}
