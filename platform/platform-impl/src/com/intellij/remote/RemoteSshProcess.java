// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.execution.process.SelfKiller;

/**
 * @author traff
 */
abstract public class RemoteSshProcess extends RemoteProcess implements SelfKiller, Tunnelable {
  /**
   * @deprecated use {@link #killProcessTree()}
   */
  @Deprecated
  protected abstract boolean hasPty();

  /**
   * @deprecated use {@link #killProcessTree()}
   */
  @Deprecated
  protected abstract boolean sendCtrlC();

  @Override
  public boolean killProcessTree() {
    if (hasPty()) {
      return sendCtrlC();
    }
    else {
      return false;
    }
  }
}
