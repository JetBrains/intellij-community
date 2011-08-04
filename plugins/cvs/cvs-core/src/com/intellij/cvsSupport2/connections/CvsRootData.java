/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.util.Comparing;

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
           && Comparing.equal(HOST, other.HOST)
           && Comparing.equal(USER, other.USER)
           && Comparing.equal(REPOSITORY, other.REPOSITORY)
           && PORT == other.PORT;
  }

  public int hashCode() {
    int methodHashCode = METHOD == null ? 0 : METHOD.hashCode();
    return methodHashCode ^ HOST.hashCode() ^ USER.hashCode() ^ REPOSITORY.hashCode() ^ PORT;
  }

  public void setPassword(String password) {
    PASSWORD = password;
  }

  public void setHost(String host) {
    HOST = host;
  }

  public void setPort(int port) {
    PORT = port;
  }

  public void setUser(String user) {
    USER = user;
  }

  public void setRepository(String repository) {
    REPOSITORY = repository;
  }

  public void setUseProxy(String proxyHost, String proxyPort) {
    CONTAINS_PROXY_INFO = true;
    PROXY_HOST = proxyHost;
    PROXY_PORT = proxyPort;
  }
}
