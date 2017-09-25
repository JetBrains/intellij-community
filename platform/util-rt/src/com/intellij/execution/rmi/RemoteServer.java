/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.execution.rmi.ssl.SslSocketFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.*;
import java.security.Security;
import java.util.Hashtable;
import java.util.Random;

public class RemoteServer {
  static {
    // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
    System.setProperty("apple.awt.UIElement", "true");
  }

  private static Remote ourRemote;

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  protected static void start(Remote remote) throws Exception {
    setupRMI();
    banJNDI();
    setupSSL();

    if (ourRemote != null) throw new AssertionError("Already started");
    ourRemote = remote;

    RMIClientSocketFactory clientSocketFactory = RMISocketFactory.getDefaultSocketFactory();
    RMIServerSocketFactory serverSocketFactory = new RMIServerSocketFactory() {
      InetAddress loopbackAddress = InetAddress.getByName("localhost");
      public ServerSocket createServerSocket(int port) throws IOException {
        return new ServerSocket(port, 0, loopbackAddress);
      }
    };

    Registry registry;
    int port;
    for (Random random = new Random(); ;) {
      port = random.nextInt(0xffff);
      if (port < 4000) continue;
      try {
        registry = LocateRegistry.createRegistry(port, clientSocketFactory, serverSocketFactory);
        break;
      }
      catch (ExportException ignored) { }
    }

    try {
      Remote stub = UnicastRemoteObject.exportObject(ourRemote, 0);
      String name = remote.getClass().getSimpleName() + Integer.toHexString(stub.hashCode());
      registry.bind(name, stub);

      String id = port + "/" + name;
      System.out.println("Port/ID: " + id);

      long waitTime = RemoteDeadHand.PING_TIMEOUT;
      Object lock = new Object();
      //noinspection InfiniteLoopStatement
      while (true) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (lock) {
          lock.wait(waitTime);
        }
        RemoteDeadHand deadHand = (RemoteDeadHand)registry.lookup(RemoteDeadHand.BINDING_NAME);
        waitTime = deadHand.ping(id);
      }
    }
    catch (Throwable e) {
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  public static void setupRMI() {
    // this properties are necessary for RMI servers to work in some cases:
    // if we are behind a firewall, if the network connection is lost, etc.

    // do not use domain or http address for server
    System.setProperty("java.rmi.server.hostname", "localhost");
    // do not use HTTP tunnelling
    System.setProperty("java.rmi.server.disableHttp", "true");
  }

  private static void banJNDI() {
    if (System.getProperty(Context.INITIAL_CONTEXT_FACTORY) == null) {
      System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "com.intellij.execution.rmi.RemoteServer$Jndi");
    }
  }

  private static void setupSSL() {
    boolean caCert = System.getProperty(SslSocketFactory.SSL_CA_CERT_PATH) != null;
    boolean clientCert = System.getProperty(SslSocketFactory.SSL_CLIENT_CERT_PATH) != null;
    boolean clientKey = System.getProperty(SslSocketFactory.SSL_CLIENT_KEY_PATH) != null;
    if (caCert || clientCert && clientKey) {
      Security.setProperty("ssl.SocketFactory.provider", "com.intellij.execution.rmi.ssl.SslSocketFactory");
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  public static class Jndi implements InitialContextFactory, InvocationHandler {
    @NotNull
    public Context getInitialContext(Hashtable<?, ?> environment) {
      return (Context)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Context.class}, this);
    }

    @Nullable
    public Object invoke(Object proxy, @NotNull Method method, Object[] args) throws Throwable {
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      }
      throw new NamingException("JNDI service is disabled");
    }
  }
}
