package com.intellij.remoteServer.runtime;

import com.intellij.openapi.util.text.StringUtil;

public enum ConnectionStatus {
  DISCONNECTED, CONNECTED, CONNECTING;

  public String getPresentableText() {
    return StringUtil.capitalize(StringUtil.toLowerCase(name()));
  }
}
