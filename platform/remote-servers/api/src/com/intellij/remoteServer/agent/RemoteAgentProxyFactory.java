// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.agent;

import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.util.List;

@ApiStatus.Internal
public interface RemoteAgentProxyFactory {

  <T> T createProxy(List<File> libraries, Class<T> agentInterface, String agentClassName) throws Exception;
}
