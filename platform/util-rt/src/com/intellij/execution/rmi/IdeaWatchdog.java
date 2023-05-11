// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.rmi;

import org.jetbrains.annotations.TestOnly;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Watchdog is used to keep its process alive while it is pinged.
 * If is supposed to be pinged with {@link #getWaitTimeoutMillis()} frequency,
 * and it remains alive for {@link #getPulseTimeoutMillis()} after the last ping.
 */
public interface IdeaWatchdog extends Remote {
  String BINDING_NAME = "_LIVE_PULSE_";

  //always throws RemoteException
  @TestOnly
  void dieNowTestOnly(int exitCode) throws RemoteException;

  /**
   * @return true if watchdog was alive and successfully died
   */
  boolean die() throws RemoteException;

  /**
   * @return true if watchdog is alive
   */
  boolean isAlive() throws RemoteException;

  /**
   * @return true if watchdog is successfully pinged
   */
  boolean ping() throws RemoteException;

  /**
   * Wait timeout determines the time period when the watchdog is alive after the last ping
   *
   * @return watchdog wait timeout
   */
  long getWaitTimeoutMillis() throws RemoteException;

  /**
   * Pulse timeout determines how often this watchdog is expected to be pinged
   *
   * @return watchdog pulse timeout
   */
  long getPulseTimeoutMillis() throws RemoteException;
}

