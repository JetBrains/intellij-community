// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.runtime;

import com.intellij.remoteServer.CloudBundle;
import org.jetbrains.annotations.Nls;

import java.util.function.Supplier;

public enum ConnectionStatus {
  DISCONNECTED(CloudBundle.messagePointer("ConnectionStatus.disconnected")),
  CONNECTED(CloudBundle.messagePointer("ConnectionStatus.connected")),
  CONNECTING(CloudBundle.messagePointer("ConnectionStatus.connecting"));

  private final Supplier<@Nls String> myPresentableText;

  public @Nls String getPresentableText() {
    return myPresentableText.get();
  }

  ConnectionStatus(Supplier<@Nls String> presentableText) {
    myPresentableText = presentableText;
  }
}
