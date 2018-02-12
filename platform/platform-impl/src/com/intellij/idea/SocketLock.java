/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.idea;

import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
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
import java.net.InetAddress;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
  private static final String TOKEN_FILE = "token";

  private static final String ACTIVATE_COMMAND = "activate ";
  private static final String PID_COMMAND = "pid";
  private static final String PATHS_EOT_RESPONSE = "---";
  private static final String OK_RESPONSE = "ok";

  private final String myConfigPath;
  private final String mySystemPath;
  private final AtomicReference<Consumer<List<String>>> myActivateListener = new AtomicReference<>();
  private String myToken;
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
        underLocks(() -> {
          FileUtil.delete(new File(myConfigPath, PORT_FILE));
          FileUtil.delete(new File(mySystemPath, PORT_FILE));
          FileUtil.delete(new File(mySystemPath, TOKEN_FILE));
          return null;
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
  public ActivateStatus lock(@NotNull String[] args) throws Exception {
    log("enter: lock(config=%s system=%s)", myConfigPath, mySystemPath);

    return underLocks(() -> {
      File portMarkerC = new File(myConfigPath, PORT_FILE);
      File portMarkerS = new File(mySystemPath, PORT_FILE);

      MultiMap<Integer, String> portToPath = MultiMap.createSmart();
      addExistingPort(portMarkerC, myConfigPath, portToPath);
      addExistingPort(portMarkerS, mySystemPath, portToPath);
      if (!portToPath.isEmpty()) {
        for (Map.Entry<Integer, Collection<String>> entry : portToPath.entrySet()) {
          ActivateStatus status = tryActivate(entry.getKey(), entry.getValue(), args);
          if (status != ActivateStatus.NO_INSTANCE) {
            log("exit: lock(): " + status);
            return status;
          }
        }
      }

      if (isShutdownCommand()) {
        System.exit(0);
      }

      myToken = UUID.randomUUID().toString();
      String[] lockedPaths = {myConfigPath, mySystemPath};
      int workerCount = PlatformUtils.isIdeaCommunity() || PlatformUtils.isDatabaseIDE() || PlatformUtils.isCidr() ? 1 : 2;
      NotNullProducer<ChannelHandler> handler = () -> new MyChannelInboundHandler(lockedPaths, myActivateListener, myToken);
      myServer = BuiltInServer.startNioOrOio(workerCount, 6942, 50, false, handler);

      byte[] portBytes = Integer.toString(myServer.getPort()).getBytes(CharsetToolkit.UTF8_CHARSET);
      FileUtil.writeToFile(portMarkerC, portBytes);
      FileUtil.writeToFile(portMarkerS, portBytes);

      File tokenFile = new File(mySystemPath, TOKEN_FILE);
      FileUtil.writeToFile(tokenFile, myToken.getBytes(CharsetToolkit.UTF8_CHARSET));
      PosixFileAttributeView view = Files.getFileAttributeView(tokenFile.toPath(), PosixFileAttributeView.class);
      if (view != null) {
        try {
          view.setPermissions(ContainerUtil.newHashSet(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
        catch (IOException e) {
          log(e);
        }
      }

      log("exit: lock(): succeed");
      return ActivateStatus.NO_INSTANCE;
    });
  }

  private <V> V underLocks(@NotNull Callable<V> action) throws Exception {
    FileUtilRt.createDirectory(new File(myConfigPath));
    Path path1 = Paths.get(myConfigPath, PORT_LOCK_FILE);
    try (FileChannel ch1 = FileChannel.open(path1, StandardOpenOption.CREATE, StandardOpenOption.APPEND); @SuppressWarnings("unused") FileLock lock1 = ch1.lock()) {
      FileUtilRt.createDirectory(new File(mySystemPath));
      Path path2 = Paths.get(mySystemPath, PORT_LOCK_FILE);
      try (FileChannel ch2 = FileChannel.open(path2, StandardOpenOption.CREATE, StandardOpenOption.APPEND); @SuppressWarnings("unused") FileLock lock2 = ch2.lock()) {
        return action.call();
      }
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
  private ActivateStatus tryActivate(int portNumber, @NotNull Collection<String> paths, @NotNull String[] args) {
    log("trying: port=%s", portNumber);
    args = checkForJetBrainsProtocolCommand(args);
    try {
      try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), portNumber)) {
        socket.setSoTimeout(5000);

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
            String token = FileUtil.loadFile(new File(mySystemPath, TOKEN_FILE));
            @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(ACTIVATE_COMMAND + token + "\0" + new File(".").getAbsolutePath() + "\0" + StringUtil.join(args, "\0"));
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
    }
    catch (ConnectException e) {
      log("%s (stale port file?)", e.getMessage());
    }
    catch (IOException e) {
      log(e);
    }

    return ActivateStatus.NO_INSTANCE;
  }

  @SuppressWarnings("ALL")
  private static void printPID(int port) {
    try {
      Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
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
    private final String myToken;
    private State myState = State.HEADER;

    public MyChannelInboundHandler(@NotNull String[] lockedPaths,
                                   @NotNull AtomicReference<Consumer<List<String>>> activateListener,
                                   @NotNull String token) {
      myLockedPaths = lockedPaths;
      myActivateListener = activateListener;
      myToken = token;
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
            if (contentLength > 8192) {
              context.close();
              return;
            }
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
              String data = command.subSequence(ACTIVATE_COMMAND.length(), command.length()).toString();
              List<String> args = StringUtil.split(data, data.contains("\0") ? "\0" : "\uFFFD");

              boolean tokenOK = !args.isEmpty() && myToken.equals(args.get(0));
              if (!tokenOK) {
                log(new UnsupportedOperationException("unauthorized request: " + command));
                Notifications.Bus.notify(new Notification(
                  Notifications.SYSTEM_MESSAGES_GROUP_ID,
                  IdeBundle.message("activation.auth.title"),
                  IdeBundle.message("activation.auth.message"),
                  NotificationType.WARNING));
              }
              else {
                Consumer<List<String>> listener = myActivateListener.get();
                if (listener != null) {
                  listener.consume(args.subList(1, args.size()));
                }
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