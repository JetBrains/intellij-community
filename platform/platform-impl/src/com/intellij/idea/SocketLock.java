/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.idea;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public class SocketLock {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.SocketLock");
  public static final int SOCKET_NUMBER_START = 6942;
  public static final int SOCKET_NUMBER_END = SOCKET_NUMBER_START + 50;

  // IMPORTANT: Some antiviral software detect viruses by the fact of accessing these ports so we should not touch them to appear innocent.
  private static final int[] FORBIDDEN_PORTS = {6953, 6969, 6970};

  private ServerSocket mySocket;
  private final List<String> myLockedPaths = new ArrayList<String>();
  private boolean myIsDialogShown = false;
  @NonNls private static final String LOCK_THREAD_NAME = "Lock thread";
  @NonNls private static final String ACTIVATE_COMMAND = "activate ";

  @Nullable
  private Consumer<List<String>> myActivateListener;

  public static enum ActivateStatus { ACTIVATED, NO_INSTANCE, CANNOT_ACTIVATE }

  public SocketLock() {
  }

  public void setActivateListener(@Nullable Consumer<List<String>> consumer) {
    myActivateListener = consumer;
  }

  public synchronized void dispose() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: destroyProcess()");
    }
    try {
      mySocket.close();
      mySocket = null;
    }
    catch (IOException e) {
      LOG.debug(e);
    }
  }

  private volatile int acquiredPort = -1;

  public synchronized int getAcquiredPort () {
    return acquiredPort;
  }

  public synchronized ActivateStatus lock(@NotNull String configPath, @NotNull String systemPath, String... args) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: lock(path='" + configPath + "')");
    }

    ActivateStatus status = ActivateStatus.NO_INSTANCE;
    acquireSocket();
    if (mySocket == null) {
      if (!myIsDialogShown) {
        final String productName = ApplicationNamesInfo.getInstance().getProductName();
        if (Main.isHeadless()) { //team server inspections
          throw new RuntimeException("Only one instance of " + productName + " can be run at a time.");
        }
        @NonNls final String pathToLogFile = PathManager.getLogPath() + "/idea.log file".replace('/', File.separatorChar);
        JOptionPane.showMessageDialog(
          JOptionPane.getRootFrame(),
          CommonBundle.message("cannot.start.other.instance.is.running.error.message", productName, pathToLogFile),
          CommonBundle.message("title.warning"),
          JOptionPane.WARNING_MESSAGE
        );
        myIsDialogShown = true;
      }
      return status;
    }

    File portMarker = new File(configPath, "port");
    try {
      FileUtil.writeToFile(portMarker, Integer.toString(acquiredPort).getBytes());
    }
    catch (IOException ignored) {
      FileUtil.asyncDelete(portMarker);
    }

    for (int i = SOCKET_NUMBER_START; i < SOCKET_NUMBER_END; i++) {
      if (isPortForbidden(i) || i == mySocket.getLocalPort()) {
        continue;
      }

      status = tryActivate(i, configPath, systemPath, args);
      if (status != ActivateStatus.NO_INSTANCE) {
        return status;
      }
    }

    myLockedPaths.add(configPath);
    myLockedPaths.add(systemPath);

    return status;
  }

  public static boolean isPortForbidden(int port) {
    for (int forbiddenPort : FORBIDDEN_PORTS) {
      if (port == forbiddenPort) return true;
    }
    return false;
  }

  @NotNull
  private static ActivateStatus tryActivate(int portNumber, @NotNull String configPath, @NotNull String systemPath, String[] args) {
    try {
      try {
        ServerSocket serverSocket = new ServerSocket(portNumber, 50, NetUtils.getLoopbackAddress());
        serverSocket.close();
        return ActivateStatus.NO_INSTANCE;
      }
      catch (IOException ignored) {
      }

      Socket socket = new Socket(NetUtils.getLoopbackAddress(), portNumber);
      socket.setSoTimeout(300);

      boolean result = false;
      DataInputStream in = new DataInputStream(socket.getInputStream());
      while (true) {
        try {
          String path = in.readUTF();
          if (path.equals(configPath) || path.equals(systemPath)) {
            result = true;
          }
        }
        catch (IOException ignored) {
          break;
        }
      }
      if (result) {
        try {
          DataOutputStream out = new DataOutputStream(socket.getOutputStream());
          out.writeUTF(ACTIVATE_COMMAND + new File(".").getAbsolutePath() + "\0" + StringUtil.join(args, "\0"));
          out.flush();
          String response = in.readUTF();
          if (response.equals("ok")) {
            return ActivateStatus.ACTIVATED;
          }
        }
        catch(IOException ignored) {
        }
        return ActivateStatus.CANNOT_ACTIVATE;
      }

      in.close();
    }
    catch (IOException e) {
      LOG.debug(e);
    }

    return ActivateStatus.NO_INSTANCE;
  }

  private int acquireSocket() {
    if (mySocket != null) return -1;

    int port = -1;

    for (int i = SOCKET_NUMBER_START; i < SOCKET_NUMBER_END; i++) {
      try {
        if (isPortForbidden(i)) continue;

        mySocket = new ServerSocket(i, 50, InetAddress.getByName("127.0.0.1"));
        port = i;
        acquiredPort = port;
        break;
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    final Thread thread = new Thread(new MyRunnable(), LOCK_THREAD_NAME);
    thread.setPriority(Thread.MIN_PRIORITY);
    thread.start();
    return port;
  }

  private class MyRunnable implements Runnable {

    @Override
    public void run() {
      try {
        while (true) {
          try {
            final Socket socket = mySocket.accept();
            socket.setSoTimeout(800);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            synchronized (SocketLock.this) {
              for (String path: myLockedPaths) {
                out.writeUTF(path);
              }
            }
            DataInputStream stream = new DataInputStream(socket.getInputStream());
            final String command = stream.readUTF();
            if (command.startsWith(ACTIVATE_COMMAND)) {
              List<String> args = StringUtil.split(command.substring(ACTIVATE_COMMAND.length()), "\0");
              if (myActivateListener != null) {
                myActivateListener.consume(args);
              }
              out.writeUTF("ok");
            }
            out.close();
          }
          catch (IOException e) {
            LOG.debug(e);
          }
        }
      }
      catch (Throwable ignored) {
      }
    }
  }
}
