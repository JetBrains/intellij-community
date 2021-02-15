// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;


public interface IdeaWatchdog extends Remote {

  String BINDING_NAME = "_LIVE_PULSE_";
  long PULSE_TIMEOUT = 9 * 1000L;
  long WAIT_TIMEOUT = 20 * 1000L;

  void die() throws RemoteException;
  boolean isAlive() throws RemoteException;
  void ping() throws RemoteException;
}

