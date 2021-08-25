// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.execution.process.PtyBasedProcess;
import com.intellij.execution.process.SelfKiller;

abstract public class RemoteSshProcess extends RemoteProcess implements SelfKiller, Tunnelable, PtyBasedProcess {
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

  @Override
  public void setWindowSize(int columns, int rows) {
    //not implemented yet; see IJ-MR-9216 && PY-40900
  }
}
