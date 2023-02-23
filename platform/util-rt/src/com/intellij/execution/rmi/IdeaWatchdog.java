// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi;

import org.jetbrains.annotations.TestOnly;

import java.rmi.Remote;
import java.rmi.RemoteException;


public interface IdeaWatchdog extends Remote {
  String BINDING_NAME = "_LIVE_PULSE_";

  //always throws RemoteException
  @TestOnly
  void dieNowTestOnly(int exitCode) throws RemoteException;

  /**
   * @return true if watchdog was alive and successfully died
   * @throws RemoteException
   */
  boolean die() throws RemoteException;

  boolean isAlive() throws RemoteException;

  boolean ping() throws RemoteException;

  long getWaitTimeoutMillis() throws RemoteException;

  long getPulseTimeoutMillis() throws RemoteException;
}

