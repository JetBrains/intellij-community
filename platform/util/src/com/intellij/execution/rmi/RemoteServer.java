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

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Hashtable;
import java.util.Random;

public class RemoteServer {
  private static Remote ourRemote;
  private static Remote ourStub;

  protected static void start(Remote remote) throws Exception {
    setupRMI();
    banJNDI();

    if (ourRemote != null) throw new AssertionError("Already started");

    ourRemote = remote;

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
      ourStub =  UnicastRemoteObject.exportObject(ourRemote, 0);
      final String name = remote.getClass().getSimpleName() + Integer.toHexString(ourStub.hashCode());
      registry.bind(name, ourStub);

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

  private static void banJNDI() {
    if (System.getProperty(InitialContext.INITIAL_CONTEXT_FACTORY) == null) {
      System.setProperty(InitialContext.INITIAL_CONTEXT_FACTORY, "com.intellij.execution.rmi.RemoteServer$Jndi");
    }
  }

  public static class Jndi implements InitialContextFactory, InvocationHandler {

    @Override
    public Context getInitialContext(final Hashtable<?, ?> environment) throws NamingException {
      return (Context) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[] { Context.class}, this );
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
      return null;
    }
  }
}
