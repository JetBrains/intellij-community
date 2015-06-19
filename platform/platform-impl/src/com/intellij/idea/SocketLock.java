/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.Consumer;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
public final class SocketLock {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.SocketLock");
  public static final int SOCKET_NUMBER_START = 6942;
  public static final int SOCKET_NUMBER_END = SOCKET_NUMBER_START + 50;

  // IMPORTANT: Some antiviral software detect viruses by the fact of accessing these ports so we should not touch them to appear innocent.
  private static final int[] FORBIDDEN_PORTS = {6953, 6969, 6970};

  private final ServerSocket serverSocket;
  private final String[] myLockedPaths = new String[2];
  @NonNls private static final String LOCK_THREAD_NAME = "Lock thread";
  @NonNls private static final String ACTIVATE_COMMAND = "activate ";

  public enum ActivateStatus {ACTIVATED, NO_INSTANCE, CANNOT_ACTIVATE}

  private final int acquiredPort;
  private volatile Consumer<List<String>> activateListener;

  public SocketLock() {
    serverSocket = acquireSocket();
    if (serverSocket == null) {
      acquiredPort = -1;

      if (!Main.isHeadless()) {
        String pathToLogFile = PathManager.getLogPath() + "/idea.log file".replace('/', File.separatorChar);
        JOptionPane.showMessageDialog(
          JOptionPane.getRootFrame(),
          CommonBundle.message("cannot.start.other.instance.is.running.error.message", ApplicationNamesInfo.getInstance().getProductName(),
                               pathToLogFile),
          CommonBundle.message("title.warning"),
          JOptionPane.WARNING_MESSAGE
        );
      }
    }
    else {
      acquiredPort = serverSocket.getLocalPort();
      Thread thread = new Thread(new MyRunnable(), LOCK_THREAD_NAME);
      thread.setPriority(Thread.MIN_PRIORITY);
      thread.start();
    }
  }

  public void setExternalInstanceListener(@Nullable Consumer<List<String>> consumer) {
    activateListener = consumer;
  }

  @TestOnly
  public void dispose() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: destroyProcess()");
    }
    try {
      serverSocket.close();
    }
    catch (IOException e) {
      LOG.debug(e);
    }
  }

  public int getAcquiredPort() {
    return acquiredPort;
  }

  private static void lockPortMarker(@NotNull File parent, @NotNull List<Closeable> list) throws IOException {
    FileUtilRt.createDirectory(parent);
    FileOutputStream stream = new FileOutputStream(new File(parent, "port.lock"), true);
    list.add(stream);
    stream.getChannel().lock();
  }

  private static void addExistingPort(@NotNull File portMarker, @NotNull String path, @NotNull MultiMap<Integer, String> portToPath) {
    if (portMarker.exists()) {
      try {
        portToPath.putValue(Integer.parseInt(FileUtilRt.loadFile(portMarker)), path);
      }
      catch (Throwable e) {
        LOG.debug(e);
        // don't delete - we overwrite it on write in any case
      }
    }
  }

  @NotNull
  public ActivateStatus lock(@NotNull String configPath, @NotNull String systemPath, String... args) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: lock(configPath='" + configPath + "', systemPath='" + systemPath + "')");
    }

    File config = new File(configPath);
    File system = new File(systemPath);
    File portMarkerC = new File(config, "port");
    File portMarkerS = new File(system, "port");
    List<Closeable> closeables = new ArrayList<Closeable>();
    try {
      lockPortMarker(config, closeables);
      lockPortMarker(system, closeables);
      MultiMap<Integer, String> portToPath = MultiMap.createSmart();
      addExistingPort(portMarkerC, configPath, portToPath);
      addExistingPort(portMarkerS, systemPath, portToPath);
      if (!portToPath.isEmpty()) {
        for (Map.Entry<Integer, Collection<String>> entry : portToPath.entrySet()) {
          ActivateStatus status = tryActivate(entry.getKey(), entry.getValue(), args);
          if (status != ActivateStatus.NO_INSTANCE) {
            return status;
          }
        }
      }

      byte[] portBytes = Integer.toString(acquiredPort).getBytes(CharsetToolkit.UTF8_CHARSET);
      FileUtil.writeToFile(portMarkerC, portBytes);
      FileUtil.writeToFile(portMarkerS, portBytes);

      myLockedPaths[0] = configPath;
      myLockedPaths[1] = systemPath;
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      for (Closeable closeable : closeables) {
        try {
          closeable.close();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }

    portMarkerC.deleteOnExit();
    portMarkerS.deleteOnExit();

    return ActivateStatus.NO_INSTANCE;
  }

  public static boolean isPortForbidden(int port) {
    for (int forbiddenPort : FORBIDDEN_PORTS) {
      if (port == forbiddenPort) return true;
    }
    return false;
  }

  @SuppressWarnings({"SocketOpenedButNotSafelyClosed", "IOResourceOpenedButNotSafelyClosed"})
  @NotNull
  private static ActivateStatus tryActivate(int portNumber, @NotNull Collection<String> paths, @NotNull String[] args) {
    Socket socket = null;
    try {
      socket = new Socket(NetUtils.getLoopbackAddress(), portNumber);
      socket.setSoTimeout(300);

      boolean result = false;
      DataInputStream in = new DataInputStream(socket.getInputStream());
      while (true) {
        try {
          String path = in.readUTF();
          if (paths.contains(path)) {
            result = true;
            // don't break - read all input
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
        catch (IOException ignored) {
        }
        return ActivateStatus.CANNOT_ACTIVATE;
      }
    }
    catch (IOException e) {
      LOG.debug(e);
    }
    finally {
      if (socket != null) {
        try {
          socket.close();
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
    }

    return ActivateStatus.NO_INSTANCE;
  }

  @Nullable
  private static ServerSocket acquireSocket() {
    for (int i = SOCKET_NUMBER_START; i < SOCKET_NUMBER_END; i++) {
      try {
        if (isPortForbidden(i)) {
          continue;
        }

        return new ServerSocket(i, 50, NetUtils.getLoopbackAddress());
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return null;
  }

  private class MyRunnable implements Runnable {
    @Override
    public void run() {
      try {
        while (true) {
          try {
            final Socket socket = serverSocket.accept();
            socket.setSoTimeout(800);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            synchronized (SocketLock.this) {
              for (String path: myLockedPaths) {
                if (path != null) {
                  out.writeUTF(path);
                }
              }
            }
            DataInputStream stream = new DataInputStream(socket.getInputStream());
            final String command = stream.readUTF();
            if (command.startsWith(ACTIVATE_COMMAND)) {
              List<String> args = StringUtil.split(command.substring(ACTIVATE_COMMAND.length()), "\0");
              Consumer<List<String>> listener = activateListener;
              if (listener != null) {
                listener.consume(args);
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
