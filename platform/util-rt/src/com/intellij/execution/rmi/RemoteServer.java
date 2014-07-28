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
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.security.Security;
import java.util.Hashtable;
import java.util.Random;

public class RemoteServer {
  static {
    // Radar #5755208: Command line Java applications need a way to launch without a Dock icon.
    System.setProperty("apple.awt.UIElement", "true");
  }

  public static final String DOMAIN_AUTH_LIBRARY_PATH = "domain.auth.library";

  private static Remote ourRemote;

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  protected static void start(Remote remote) throws Exception {
    setupRMI();
    banJNDI();
    setupSSL();
    setupDomainAuth();

    if (ourRemote != null) throw new AssertionError("Already started");
    ourRemote = remote;

    Registry registry;
    int port;
    for (Random random = new Random(); ;) {
      port = random.nextInt(0xffff);
      if (port < 4000) continue;
      try {
        registry = LocateRegistry.createRegistry(port);
        break;
      }
      catch (ExportException ignored) { }
    }

    try {
      Remote stub = UnicastRemoteObject.exportObject(ourRemote, 0);
      final String name = remote.getClass().getSimpleName() + Integer.toHexString(stub.hashCode());
      registry.bind(name, stub);

      String id = port + "/" + name;
      System.out.println("Port/ID: " + id);

      long waitTime = 2 * 60 * 1000L;
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

  private static void setupDomainAuth() {
    String property = System.getProperty(DOMAIN_AUTH_LIBRARY_PATH);
    if (property != null) {
      try {
        File extracted = extractLibraryFromJar(property);
        setLibraryPath(extracted.getParentFile().getAbsolutePath());
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  public static class Jndi implements InitialContextFactory, InvocationHandler {
    @NotNull
    public Context getInitialContext(final Hashtable<?, ?> environment) throws NamingException {
      return (Context)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Context.class}, this);
    }

    @Nullable
    public Object invoke(final Object proxy, @NotNull final Method method, final Object[] args) throws Throwable {
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      }
      throw new NamingException("JNDI service is disabled");
    }
  }

  @NotNull
  public static File extractLibraryFromJar(@NotNull String path) throws Exception {
    if (!path.startsWith("/")) throw new IllegalArgumentException("The path to be absolute (start with '/').");

    String[] parts = path.split("/");
    String filename = parts.length > 1 ? parts[parts.length - 1] : null;

    if (filename == null) throw new IllegalArgumentException("The filename extracted from the path: '" + path + "' is null");

    File auth = FileUtilRt.createTempDirectory("win_auth", null, true);
    File temp = new File(auth, filename);
    temp.deleteOnExit();
    if (!temp.createNewFile() || !temp.exists()) throw new FileNotFoundException("File " + temp.getAbsolutePath() + " does not exist.");

    byte[] buffer = new byte[5 * 1024];
    int readBytes;

    //noinspection IOResourceOpenedButNotSafelyClosed
    InputStream is = RemoteServer.class.getResourceAsStream(path);
    if (is == null) throw new FileNotFoundException("File " + path + " was not found inside JAR.");

    OutputStream os = new FileOutputStream(temp);
    try {
      while ((readBytes = is.read(buffer)) != -1) os.write(buffer, 0, readBytes);
    }
    finally {
      os.close();
      is.close();
    }
    return temp;
  }

  private static void setLibraryPath(@NotNull String path) throws NoSuchFieldException, IllegalAccessException {
    System.setProperty("java.library.path", path);
    Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
    fieldSysPath.setAccessible(true);
    fieldSysPath.set(null, null);
  }
}
