package com.intellij.remoteServer.runtime;

import com.intellij.remoteServer.CloudBundle;
import org.jetbrains.annotations.Nls;

import java.util.function.Supplier;

public enum ConnectionStatus {
  DISCONNECTED(CloudBundle.messagePointer("ConnectionStatus.disconnected")),
  CONNECTED(CloudBundle.messagePointer("ConnectionStatus.connected")),
  CONNECTING(CloudBundle.messagePointer("ConnectionStatus.connecting"));

  private final Supplier<@Nls String> myPresentableText;

  @Nls
  public String getPresentableText() {
    return myPresentableText.get();
  }

  ConnectionStatus(Supplier<@Nls String> presentableText) {
    myPresentableText = presentableText;
  }
}
