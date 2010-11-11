/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Random;

public class RemoteServer {
  protected static void start(Remote remote) throws Exception {
    setupRMI();

    Registry registry;
    int port = 0;
    for (Random random = new Random(); ;) {
      port = random.nextInt(0xffff);
      if (port < 4000) continue;
      try {
        registry = LocateRegistry.createRegistry(port);
        break;
      }
      catch (ExportException ex) {
      }
    }
    try {
      final Remote stub = UnicastRemoteObject.exportObject(remote, 0);
      final String name = remote.getClass().getSimpleName() + Integer.toHexString(stub.hashCode());
      registry.bind(name, stub);

      System.out.println("Port/ID:" + port + "/" + name);
      Object lock = new Object();
      synchronized (lock) {
        lock.wait();
      }
    }
    catch (Throwable e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  public static void setupRMI() {
    // this properties are necessary for RMI servers to work in some cases:
    // if we are behind a firewall, if the network connection is lost, etc.

    // do not use domain or http address for server
    System.setProperty("java.rmi.server.hostname", "localhost");
    // do not use http tunnelling
    System.setProperty("java.rmi.server.disableHttp", "true");
  }
}
