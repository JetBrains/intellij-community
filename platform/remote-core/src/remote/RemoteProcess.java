// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.google.common.net.HostAndPort;
import com.intellij.execution.process.ProcessTreeKiller;
import com.intellij.execution.process.SelfKiller;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Koshevoy
 */
public abstract class RemoteProcess extends Process implements SelfKiller, ProcessTreeKiller {
  //Also, it emulates pid for UnixProcessManager.getProcessId
  private static final int pid = -1;

  @Override
  public abstract boolean killProcessTree();

  public abstract boolean isDisconnected();

  @Override
  public long pid() {
    return pid;
  }

  /**
   * Returns host and port which one should connect to get to the process remote port.
   * Returns {@code null} if connection to the remote port is impossible or there is no information about it.
   *
   * @param remotePort remote process port
   * @return host:port
   */
  @Nullable
  public abstract HostAndPort getLocalTunnel(int remotePort);

  public abstract void setWindowSize(int columns, int rows);
}
