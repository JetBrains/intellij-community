// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import org.jetbrains.annotations.Nullable;

public interface MutableRemoteCredentialsEx extends MutableRemoteCredentials {

  void setLocalPort(int port);

  void setLiteralLocalPort(@Nullable String portText);

  int getLocalPort();

  @Nullable
  String getLiteralLocalPort();
}
