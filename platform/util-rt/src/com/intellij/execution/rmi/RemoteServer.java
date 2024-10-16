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
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.security.Security;
import java.util.Hashtable;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteServer {

  public static final String SERVER_HOSTNAME = "java.rmi.server.hostname";
  private static final Pattern REF_ENDPOINT_PATTERN = Pattern.compile("endpoint:\\[.*:(\\d+)]");

  static {
    // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
    System.setProperty("apple.awt.UIElement", "true");
  }

  private static Remote ourRemote;

  protected static void start(Remote remote) throws Exception {
    start(remote, true, false);
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  protected static void start(Remote remote, boolean localHostOnly, boolean spinForever) throws Exception {
    IdeaWatchdog watchdog = new IdeaWatchdogImpl();
    if (remote instanceof IdeaWatchdogAware) {
      ((IdeaWatchdogAware)remote).setWatchdog(watchdog);
    }

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
      Remote stub = UnicastRemoteObject.exportObject(ourRemote, 0);
      String name = remote.getClass().getSimpleName() + Integer.toHexString(stub.hashCode());
      registry.bind(name, stub);

      // the first used port will be reused for all further exported objects,
      // unless they're exported with explicit port other than 0, or with custom socket factories
      Matcher matcher = REF_ENDPOINT_PATTERN.matcher(stub.toString());
      String servicesPort = matcher.find() ? matcher.group(1) : "0";

      Remote watchdogStub = UnicastRemoteObject.exportObject(watchdog, 0);
      registry.bind(IdeaWatchdog.BINDING_NAME, watchdogStub);

      System.out.println("Port/ServicesPort/ID: " + (port + "/" + servicesPort + "/" + name));
      System.out.println();

      if (!spinForever) {
        spinWhileWatchdogAlive(watchdog);
      }
    }
    catch (Throwable e) {
      e.printStackTrace(System.err);
      System.exit(2);
    }
  }

  private static void spinWhileWatchdogAlive(IdeaWatchdog watchdog) throws Exception {
    long waitTime = watchdog.getWaitTimeoutMillis();
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
          Socket socket = new Socket(host, port);
          socket.setKeepAlive(true);
          return socket;
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
    setupDisabledAlgorithms();
    String caCertPath = System.getProperty(SslUtil.SSL_CA_CERT_PATH);
    boolean caCert = caCertPath != null;
    String clientCertPath = System.getProperty(SslUtil.SSL_CLIENT_CERT_PATH);
    String clientKeyPath = System.getProperty(SslUtil.SSL_CLIENT_KEY_PATH);
    boolean clientKey = clientKeyPath != null;
    boolean deferred = "true".equals(System.getProperty(SslKeyStore.SSL_DEFERRED_KEY_LOADING));
    boolean deferredCa = "true".equals(System.getProperty(SslKeyStore.SSL_DEFERRED_CA_LOADING));
    boolean useFactory = "true".equals(System.getProperty(SslUtil.SSL_USE_FACTORY));
    if (useFactory) {
      if (caCert || clientKey) {
        Security.setProperty("ssl.SocketFactory.provider", SslSocketFactory.class.getName());
      }
    }
    else {
      if (caCert || deferredCa) SslTrustStore.setDefault();
      if (clientKey || deferred) SslKeyStore.setDefault();
    }
    if (caCert) {
      SslTrustStore.appendUserCert("user-provided-ca", caCertPath);
    }
    if (clientKey) {
      SslKeyStore.loadKey("user-provided-key", clientKeyPath, clientCertPath, null);
    }
  }

  private static void setupDisabledAlgorithms() {
    passSecurityProperty("jdk.certpath.disabledAlgorithms");
    passSecurityProperty("jdk.tls.disabledAlgorithms");
  }

  private static void passSecurityProperty(String propertyName) {
    String value = System.getProperty(propertyName);
    if (value != null) Security.setProperty(propertyName, value);
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
