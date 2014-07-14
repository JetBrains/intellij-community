package com.intellij.remote;

import com.intellij.execution.process.SelfKiller;

/**
 * @author traff
 */
abstract public class RemoteSshProcess extends Process implements SelfKiller {
  /**
   * Makes host:localPort server which is available on local side available on remote side as localhost:remotePort.
   */
  public abstract void addRemoteTunnel(int remotePort, String host, int localPort) throws RemoteSdkException;

  /**
   * Makes host:remotePort server which is available on remote side available on local side as localhost:localPort.
   */
  public abstract void addLocalTunnel(int localPort, String host, int remotePort) throws RemoteSdkException;

  public abstract boolean hasPty();

  public abstract boolean sendCtrlC();
}
