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

  public boolean killProcessTree() {
    if (hasPty()) {
      return sendCtrlC();
    }
    else {
      return false;
    }
  }
}
