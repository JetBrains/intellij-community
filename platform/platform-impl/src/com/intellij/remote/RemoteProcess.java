/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.remote;

import com.google.common.net.HostAndPort;
import com.intellij.execution.process.SelfKiller;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Koshevoy
 */
public abstract class RemoteProcess extends Process implements SelfKiller {
  public abstract boolean killProcessTree();

  public abstract boolean isDisconnected();

  /**
   * Returns host and port which one should connect to get to the process remote port.
   * Returns {@code null} if connection to the remote port is impossible or there is no information about it.
   *
   * @param remotePort remote process port
   * @return host:port
   */
  @Nullable
  public abstract HostAndPort getLocalTunnel(int remotePort);
}
