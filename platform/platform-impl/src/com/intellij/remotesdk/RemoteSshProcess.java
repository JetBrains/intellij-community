package com.intellij.remotesdk;

/**
 * @author traff
 */
abstract public class RemoteSshProcess extends Process {
  public abstract void addRemoteTunnel(int remotePort, String host, int localPort) throws RemoteInterpreterException;

  public abstract void addLocalTunnel(int localPort, String host, int remotePort) throws RemoteInterpreterException;

  public abstract boolean hasPty();

  public abstract boolean sendCtrlC();
}
