/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author gregsh
 */
public interface RemoteDeadHand extends Remote {

  String BINDING_NAME = "_DEAD_HAND_";
  long PING_TIMEOUT = 20 * 1000L;

  /**
   * @return time in milliseconds to wait till the next ping.
   * Negative return value means "terminate", 0 means "live forever" and should be avoided.
   */
  long ping(String id) throws RemoteException;

  class TwoMinutesTurkish extends RemoteObject implements RemoteDeadHand {

    private static final TwoMinutesTurkish ourCook = new TwoMinutesTurkish();
    private static final Remote ourHand;
    private static final AtomicLong ourAskedThatManyTimes = new AtomicLong();

    static {
      Remote remote;
      try {
        remote = UnicastRemoteObject.exportObject(ourCook, 0);
      }
      catch (RemoteException e) {
        throw new IllegalStateException(e);
      }
      ourHand = remote;
    }

    public long ping(String id) throws RemoteException {
      ourAskedThatManyTimes.incrementAndGet();
      return PING_TIMEOUT;
    }

    public static void startCooking(String host, int port) throws Exception {
      Registry registry = LocateRegistry.getRegistry(host, port);
      registry.bind(BINDING_NAME, ourHand);
    }
  }
}
