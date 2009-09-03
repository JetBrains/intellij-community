/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.cvsSupport2.connections;

public interface CvsSettings {
  void setPassword(String password);
  void setHost(String host);
  void setPort(int port);
  void setUser(String user);
  void setRepository(String repository);
  void setUseProxy(String proxyHost, String proxyPort);    
}
