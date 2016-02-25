/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.NotNullProducer;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.net.NetUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.BuiltInServer;
import org.jetbrains.io.MessageDecoder;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.net.Socket;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * @author mike
 */
public final class SocketLock {
  public enum ActivateStatus {ACTIVATED, NO_INSTANCE, CANNOT_ACTIVATE}

  private static final String PORT_FILE = "port";
  private static final String PORT_LOCK_FILE = "port.lock";
  private static final String ACTIVATE_COMMAND = "activate ";
  private static final String PID_COMMAND = "pid";
  private static final String PATHS_EOT_RESPONSE = "---";
  private static final String OK_RESPONSE = "ok";

  private final String myConfigPath;
  private final String mySystemPath;
  private final AtomicReference<Consumer<List<String>>> myActivateListener = new AtomicReference<Consumer<List<String>>>();
  private BuiltInServer myServer;

  public SocketLock(@NotNull String configPath, @NotNull String systemPath) {
    myConfigPath = canonicalPath(configPath);
    mySystemPath = canonicalPath(systemPath);
  }

  public void setExternalInstanceListener(@Nullable Consumer<List<String>> consumer) {
    myActivateListener.set(consumer);
  }

  public void dispose() {
    log("enter: dispose()");

    BuiltInServer server = myServer;
    if (server == null) return;

    try {
      Disposer.dispose(server);
    }
    finally {
      try {
        underLocks(new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            FileUtil.delete(new File(myConfigPath, PORT_FILE));
            FileUtil.delete(new File(mySystemPath, PORT_FILE));
            return null;
          }
        });
      }
      catch (Exception e) {
        Logger.getInstance(SocketLock.class).warn(e);
      }
    }
  }

  @Nullable
  public BuiltInServer getServer() {
    return myServer;
  }

  @NotNull
  public ActivateStatus lock() throws Exception {
    return lock(ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @NotNull
  public ActivateStatus lock(@NotNull final String[] args) throws Exception {
    log("enter: lock(config=%s system=%s)", myConfigPath, mySystemPath);

    return underLocks(new Callable<ActivateStatus>() {
      @Override
      public ActivateStatus call() throws Exception {
        File portMarkerC = new File(myConfigPath, PORT_FILE);
        File portMarkerS = new File(mySystemPath, PORT_FILE);

        MultiMap<Integer, String> portToPath = MultiMap.createSmart();
        addExistingPort(portMarkerC, myConfigPath, portToPath);
        addExistingPort(portMarkerS, mySystemPath, portToPath);
        if (!portToPath.isEmpty()) {
          for (Map.Entry<Integer, Collection<String>> entry : portToPath.entrySet()) {
            ActivateStatus status = tryActivate(entry.getKey(), entry.getValue(), args);
            if (status != ActivateStatus.NO_INSTANCE) {
              return status;
            }
          }
        }

        if (isShutdownCommand()) {
          System.exit(0);
        }
        final String[] lockedPaths = {myConfigPath, mySystemPath};
        int workerCount = PlatformUtils.isIdeaCommunity() || PlatformUtils.isDatabaseIDE() || PlatformUtils.isCidr() ? 1 : 2;
        myServer = BuiltInServer.startNioOrOio(workerCount, 6942, 50, false, new NotNullProducer<ChannelHandler>() {
          @NotNull
          @Override
          public ChannelHandler produce() {
            return new MyChannelInboundHandler(lockedPaths, myActivateListener);
          }
        });
        byte[] portBytes = Integer.toString(myServer.getPort()).getBytes(CharsetToolkit.UTF8_CHARSET);
        FileUtil.writeToFile(portMarkerC, portBytes);
        FileUtil.writeToFile(portMarkerS, portBytes);
        log("exit: lock(): succeed");
        return ActivateStatus.NO_INSTANCE;
      }
    });
  }

  private <V> V underLocks(@NotNull Callable<V> action) throws Exception {
    FileUtilRt.createDirectory(new File(myConfigPath));
    FileOutputStream lock1 = new FileOutputStream(new File(myConfigPath, PORT_LOCK_FILE), true);
    try {
      FileUtilRt.createDirectory(new File(mySystemPath));
      FileOutputStream lock2 = new FileOutputStream(new File(mySystemPath, PORT_LOCK_FILE), true);
      try {
        return action.call();
      }
      finally {
        lock2.close();
      }
    }
    finally {
      lock1.close();
    }
  }

  private static void addExistingPort(@NotNull File portMarker, @NotNull String path, @NotNull MultiMap<Integer, String> portToPath) {
    if (portMarker.exists()) {
      try {
        portToPath.putValue(Integer.parseInt(FileUtilRt.loadFile(portMarker)), path);
      }
      catch (Exception e) {
        log(e);
        // don't delete - we overwrite it on write in any case
      }
    }
  }

  @NotNull
  private static ActivateStatus tryActivate(int portNumber, @NotNull Collection<String> paths, @NotNull String[] args) {
    log("trying: port=%s", portNumber);
    args = checkForJetBrainsProtocolCommand(args);
    try {
      Socket socket = new Socket(NetUtils.getLoopbackAddress(), portNumber);
      try {
        socket.setSoTimeout(1000);

        boolean result = false;
        @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataInputStream in = new DataInputStream(socket.getInputStream());
        while (true) {
          try {
            String path = in.readUTF();
            log("read: path=%s", path);
            if (PATHS_EOT_RESPONSE.equals(path)) {
              break;
            }
            else if (paths.contains(path)) {
              result = true;  // don't break - read all input
            }
          }
          catch (IOException e) {
            log("read: %s", e.getMessage());
            break;
          }
        }

        if (result) {
          try {
            @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(ACTIVATE_COMMAND + new File(".").getAbsolutePath() + "\0" + StringUtil.join(args, "\0"));
            out.flush();
            String response = in.readUTF();
            log("read: response=%s", response);
            if (response.equals(OK_RESPONSE)) {
              if (isShutdownCommand()) {
                printPID(portNumber);
              }
              return ActivateStatus.ACTIVATED;
            }
          }
          catch (IOException e) {
            log(e);
          }

          return ActivateStatus.CANNOT_ACTIVATE;
        }
      }
      finally {
        socket.close();
      }
    }
    catch (ConnectException e) {
      log("%s (stale port file?)", e.getMessage());
    }
    catch (IOException e) {
      log(e);
    }

    return ActivateStatus.NO_INSTANCE;
  }

  private static void printPID(int port) {
    try {
      Socket socket = new Socket(NetUtils.getLoopbackAddress(), port);
      socket.setSoTimeout(1000);
      @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      out.writeUTF(PID_COMMAND);
      DataInputStream in = new DataInputStream(socket.getInputStream());
      int pid = 0;
      while (true) {
        try {
          String s = in.readUTF();
          if (Pattern.matches("[0-9]+@.*", s)) {
            pid = Integer.parseInt(s.substring(0, s.indexOf('@')));
            System.err.println(pid);
          }
        }catch (IOException e) {
          break;
        }
      }
    }
    catch (Exception ignore) {
    }
  }

  private static boolean isShutdownCommand() {
    return "shutdown".equals(JetBrainsProtocolHandler.getCommand());
  }

  private static String[] checkForJetBrainsProtocolCommand(String[] args) {
    final String jbUrl = System.getProperty(JetBrainsProtocolHandler.class.getName());
    if (jbUrl != null) {
      return new String[]{jbUrl};
    }
    return args;
  }

  private static class MyChannelInboundHandler extends MessageDecoder {
    private enum State {HEADER, CONTENT}

    private final String[] myLockedPaths;
    private final AtomicReference<Consumer<List<String>>> myActivateListener;
    private State myState = State.HEADER;

    public MyChannelInboundHandler(@NotNull String[] lockedPaths, @NotNull AtomicReference<Consumer<List<String>>> activateListener) {
      myLockedPaths = lockedPaths;
      myActivateListener = activateListener;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
      ByteBuf buffer = context.alloc().ioBuffer(1024);
      boolean success = false;
      try {
        ByteBufOutputStream out = new ByteBufOutputStream(buffer);
        for (String path : myLockedPaths) out.writeUTF(path);
        out.writeUTF(PATHS_EOT_RESPONSE);
        out.close();
        success = true;
      }
      finally {
        if (!success) {
          buffer.release();
        }
      }
      context.writeAndFlush(buffer);
    }

    @Override
    protected void messageReceived(@NotNull ChannelHandlerContext context, @NotNull ByteBuf input) throws Exception {
      while (true) {
        switch (myState) {
          case HEADER: {
            ByteBuf buffer = getBufferIfSufficient(input, 2, context);
            if (buffer == null) {
              return;
            }

            contentLength = buffer.readUnsignedShort();
            myState = State.CONTENT;
          }
          break;

          case CONTENT: {
            CharSequence command = readChars(input);
            if (command == null) {
              return;
            }

            if (StringUtil.startsWith(command, PID_COMMAND)) {
              ByteBuf buffer = context.alloc().ioBuffer();
              ByteBufOutputStream out = new ByteBufOutputStream(buffer);
              String name = ManagementFactory.getRuntimeMXBean().getName();
              out.writeUTF(name);
              out.close();
              context.writeAndFlush(buffer);
            }

            if (StringUtil.startsWith(command, ACTIVATE_COMMAND)) {
              List<String> args = StringUtil.split(command.subSequence(ACTIVATE_COMMAND.length(), command.length()).toString(), "\0");
              Consumer<List<String>> listener = myActivateListener.get();
              if (listener != null) {
                listener.consume(args);
              }

              ByteBuf buffer = context.alloc().ioBuffer(4);
              ByteBufOutputStream out = new ByteBufOutputStream(buffer);
              out.writeUTF(OK_RESPONSE);
              out.close();
              context.writeAndFlush(buffer);
            }
            context.close();
          }
          break;
        }
      }
    }
  }

  @NotNull
  private static String canonicalPath(@NotNull String configPath) {
    try {
      return new File(configPath).getCanonicalPath();
    }
    catch (IOException ignore) {
      return configPath;
    }
  }

  private static void log(Exception e) {
    Logger.getInstance(SocketLock.class).warn(e);
  }

  private static void log(String format, Object... args) {
    Logger logger = Logger.getInstance(SocketLock.class);
    if (logger.isDebugEnabled()) {
      logger.debug(String.format(format, args));
    }
  }
}