package com.intellij.remoteServer.runtime;

import com.intellij.openapi.util.text.StringUtil;

/**
 * @author nik
 */
public enum ConnectionStatus {
  DISCONNECTED, CONNECTED, CONNECTING;

  public String getPresentableText() {
    return StringUtil.capitalize(name().toLowerCase());
  }
}
