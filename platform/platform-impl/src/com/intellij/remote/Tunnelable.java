// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

public interface Tunnelable {
  /**
   * Makes host:localPort server which is available on local side available on remote side as localhost:remotePort.
   */
  void addRemoteTunnel(int remotePort, String host, int localPort) throws RemoteSdkException;

  /**
   * Makes host:remotePort server which is available on remote side available on local side as localhost:localPort.
   */
  void addLocalTunnel(int localPort, int remotePort) throws RemoteSdkException;

}
