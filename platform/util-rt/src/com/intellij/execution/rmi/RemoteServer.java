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

import com.intellij.execution.rmi.ssl.SslKeyStore;
import com.intellij.execution.rmi.ssl.SslSocketFactory;
import com.intellij.execution.rmi.ssl.SslTrustStore;
import com.intellij.execution.rmi.ssl.SslUtil;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.security.Security;
import java.util.Hashtable;
import java.util.Random;

public class RemoteServer {

  public static final String SERVER_HOSTNAME = "java.rmi.server.hostname";

  static {
    // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
    System.setProperty("apple.awt.UIElement", "true");
  }

  private static Remote ourRemote;

  protected static void start(Remote remote) throws Exception {
    start(remote, false, true);
  }

  /**
   * @param requireKnownPort when true, the port for the exported Remote will be written to stdout together with serverPort/name pair
   */
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  protected static void start(Remote remote, boolean requireKnownPort, boolean localHostOnly) throws Exception {
    setupRMI(localHostOnly);
    banJNDI();
    setupSSL();

    if (ourRemote != null) throw new AssertionError("Already started");
    ourRemote = remote;

    Registry registry;
    int port;
    for (Random random = new Random(); ; ) {
      port = random.nextInt(0xffff);
      if (port < 4000) continue;
      try {
        if (localHostOnly) {
          registry = LocateRegistry.createRegistry(port);
        }
        else {
          registry = LocateRegistry.createRegistry(port, null, RMISocketFactory.getSocketFactory());
        }


        break;
      }
      catch (ExportException ignored) {
      }
    }

    try {
      Remote stub;
      String portSuffix;
      if (requireKnownPort) {
        Pair<Remote, Integer> exported = export(ourRemote);
        stub = exported.first;
        portSuffix = "#" + exported.second;
      }
      else {
        stub = UnicastRemoteObject.exportObject(ourRemote, 0);
        portSuffix = "";
      }
      String name = remote.getClass().getSimpleName() + Integer.toHexString(stub.hashCode());
      registry.bind(name, stub);

      IdeaWatchdog watchdog = new IdeaWatchdogImpl();
      Remote watchdogStub = UnicastRemoteObject.exportObject(watchdog, 0);
      registry.bind(IdeaWatchdog.BINDING_NAME, watchdogStub);
      String id = port + "/" + name + portSuffix;
      System.out.println("Port/ID: " + id);
      System.out.println();

      spinUntilWatchdogAlive(watchdog);
    }
    catch (Throwable e) {
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  private static void spinUntilWatchdogAlive(IdeaWatchdog watchdog) throws Exception {
    long waitTime = IdeaWatchdog.WAIT_TIMEOUT;
    Object lock = new Object();
    while (true) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (lock) {
        lock.wait(waitTime);
      }
      if (!watchdog.isAlive()) {
        System.exit(1);
      }
    }
  }

  private static Pair<Remote, Integer> export(Remote toExport) {
    //[Mihail Muhin] wasn't able to find a better way to know the port. A good alternative would be exporting to port 0 and extracting
    // serving port number after, but there seem to be no way to extract port for a Remote
    int port;
    for (Random random = new Random(); ; ) {
      port = random.nextInt(0xffff);
      if (port < 4000) continue;
      try {
        Remote remote = UnicastRemoteObject.exportObject(toExport, port);
        return new Pair<Remote, Integer>(remote, port);
      }
      catch (RemoteException ignored) {
      }
    }
  }

  public static void setupRMI(final boolean localHostOnly) {
    // this properties are necessary for RMI servers to work in some cases:
    // if we are behind a firewall, if the network connection is lost, etc.

    // do not use domain or http address for server
    if (System.getProperty(SERVER_HOSTNAME) == null) {
      System.setProperty(SERVER_HOSTNAME, getLoopbackAddress());
    }
    // do not use HTTP tunnelling
    System.setProperty("java.rmi.server.disableHttp", "true");

    if (RMISocketFactory.getSocketFactory() != null) return;
    try {
      RMISocketFactory.setSocketFactory(new RMISocketFactory() {
        final InetAddress loopbackAddress = InetAddress.getByName(getListenAddress(localHostOnly));

        @Override
        public Socket createSocket(String host, int port) throws IOException {
          return new Socket(host, port);
        }

        @Override
        public ServerSocket createServerSocket(int port) throws IOException {
          return new ServerSocket(port, 0, loopbackAddress);
        }
      });
    }
    catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static void banJNDI() {
    if (System.getProperty(Context.INITIAL_CONTEXT_FACTORY) == null) {
      System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "com.intellij.execution.rmi.RemoteServer$Jndi");
    }
  }

  private static void setupSSL() {
    boolean caCert = System.getProperty(SslUtil.SSL_CA_CERT_PATH) != null;
    boolean clientCert = System.getProperty(SslUtil.SSL_CLIENT_CERT_PATH) != null;
    boolean clientKey = System.getProperty(SslUtil.SSL_CLIENT_KEY_PATH) != null;
    boolean deferred = "true".equals(System.getProperty(SslKeyStore.SSL_DEFERRED_KEY_LOADING));
    boolean useFactory = "true".equals(System.getProperty(SslUtil.SSL_USE_FACTORY));
    if (useFactory) {
      if (caCert || clientCert && clientKey) {
        Security.setProperty("ssl.SocketFactory.provider", SslSocketFactory.class.getName());
      }
    }
    else {
      if (caCert) SslTrustStore.setDefault();
      if (clientCert && clientKey || deferred) SslKeyStore.setDefault();
    }
  }


  private static String getListenAddress(boolean localHostOnly) {
    if (localHostOnly) {
      return getLoopbackAddress();
    }
    return isIpV6() ? "::/0" : "0.0.0.0";
  }

  @NotNull
  private static String getLoopbackAddress() {
    return isIpV6() ? "::1" : "127.0.0.1";
  }

  private static boolean isIpV6() {
    try {
      return InetAddress.getByName(null) instanceof Inet6Address;
    }
    catch (IOException ignore) {
      return false;
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
