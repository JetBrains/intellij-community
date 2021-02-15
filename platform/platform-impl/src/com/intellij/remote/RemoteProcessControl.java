// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.google.common.net.HostAndPort;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

public interface RemoteProcessControl extends ProcessControlWithMappings {

  Pair<String, Integer> getRemoteSocket(int localPort) throws RemoteSdkException;

  @Nullable
  HostAndPort getLocalTunnel(int remotePort);
}
