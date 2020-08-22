// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.CliResult;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.BuiltInServer;
import org.jetbrains.io.MessageDecoder;

import java.awt.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class SocketLock {
  public enum ActivationStatus {ACTIVATED, NO_INSTANCE, CANNOT_ACTIVATE}

  /**
   * Name of an environment variable that will be set by the Windows launcher and will contain the working directory the
   * IDE was started with.
   *
   * This is necessary on Windows because the launcher needs to change the current directory for the JVM to load
   * properly; see the details in WindowsLauncher.cpp.
   */
  public static final String LAUNCHER_INITIAL_DIRECTORY_ENV_VAR = "IDEA_INITIAL_DIRECTORY";

  private static final String PORT_FILE = "port";
  private static final String PORT_LOCK_FILE = "port.lock";
  private static final String TOKEN_FILE = "token";

  private static final String ACTIVATE_COMMAND = "activate ";
  private static final String PID_COMMAND = "pid";
  private static final String OK_RESPONSE = "ok";
  private static final String PATHS_EOT_RESPONSE = "---";

  private final AtomicReference<Function<List<String>, Future<CliResult>>> myCommandProcessorRef;
  private final Path myConfigPath;
  private final Path mySystemPath;
  private final List<AutoCloseable> myLockedFiles = new ArrayList<>(4);
  private volatile CompletableFuture<BuiltInServer> myBuiltinServerFuture;

  public SocketLock(@NotNull Path configPath, @NotNull Path systemPath) {
    myConfigPath = configPath;
    mySystemPath = systemPath;
    if (myConfigPath.equals(mySystemPath)) {
      throw new IllegalArgumentException("'config' and 'system' paths should point to different directories");
    }
    myCommandProcessorRef = new AtomicReference<>(args -> CliResult.error(Main.ACTIVATE_NOT_INITIALIZED, IdeBundle.message("activation.not.initialized")));
  }

  public @NotNull Path getConfigPath() {
    return myConfigPath;
  }

  public @NotNull Path getSystemPath() {
    return mySystemPath;
  }

  public void setCommandProcessor(@Nullable Function<List<String>, Future<CliResult>> processor) {
    myCommandProcessorRef.set(processor);
  }

  public void dispose() {
    log("enter: dispose()");

    BuiltInServer server = null;
    try {
      server = getServer();
    }
    catch (Exception ignored) { }

    try {
      if (myLockedFiles.isEmpty()) {
        lockPortFiles();
      }
      if (server != null) {
        Disposer.dispose(server);
      }
      Files.deleteIfExists(myConfigPath.resolve(PORT_FILE));
      Files.deleteIfExists(mySystemPath.resolve(PORT_FILE));
      Files.deleteIfExists(mySystemPath.resolve(TOKEN_FILE));
      unlockPortFiles();
    }
    catch (Exception e) {
      log(e);
    }
  }

  @Nullable BuiltInServer getServer() {
    Future<BuiltInServer> future = myBuiltinServerFuture;
    if (future != null) {
      try {
        return future.get();
      }
      catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
      catch (ExecutionException e) {
        throw new IllegalStateException(e.getCause());
      }
    }
    return null;
  }

  @Nullable CompletableFuture<BuiltInServer> getServerFuture() {
    return myBuiltinServerFuture;
  }

  public @NotNull Map.Entry<ActivationStatus, CliResult> lockAndTryActivate(@NotNull String @NotNull [] args) throws Exception {
    log("enter: lock(config=%s system=%s)", myConfigPath, mySystemPath);

    lockPortFiles();

    Map<Integer, List<String>> portToPath = new HashMap<>();
    readPort(myConfigPath, portToPath);
    readPort(mySystemPath, portToPath);
    if (!portToPath.isEmpty()) {
      args = JetBrainsProtocolHandler.checkForJetBrainsProtocolCommand(args);
      for (Map.Entry<Integer, List<String>> entry : portToPath.entrySet()) {
        Map.Entry<ActivationStatus, CliResult> status = tryActivate(entry.getKey(), entry.getValue(), args);
        if (status.getKey() != ActivationStatus.NO_INSTANCE) {
          log("exit: lock(): " + status.getValue());
          unlockPortFiles();
          return status;
        }
      }
    }

    if (JetBrainsProtocolHandler.isShutdownCommand()) {
      unlockPortFiles();
      System.exit(0);
    }

    myBuiltinServerFuture = CompletableFuture.supplyAsync(() -> {
      Activity activity = StartUpMeasurer.startActivity("built-in server launch");

      String token = UUID.randomUUID().toString();
      Path[] lockedPaths = {myConfigPath, mySystemPath};
      BuiltInServer server = BuiltInServer.start(6942, 50, () -> new MyChannelInboundHandler(lockedPaths, myCommandProcessorRef, token));
      try {
        byte[] portBytes = Integer.toString(server.getPort()).getBytes(StandardCharsets.UTF_8);
        Files.write(myConfigPath.resolve(PORT_FILE), portBytes);
        Files.write(mySystemPath.resolve(PORT_FILE), portBytes);

        Path tokenFile = mySystemPath.resolve(TOKEN_FILE);
        Files.write(tokenFile, token.getBytes(StandardCharsets.UTF_8));
        PosixFileAttributeView view = Files.getFileAttributeView(tokenFile, PosixFileAttributeView.class);
        if (view != null) {
          try {
            view.setPermissions(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
          }
          catch (IOException e) {
            log(e);
          }
        }

        unlockPortFiles();
      }
      catch (Exception e) {
        ExceptionUtilRt.rethrowUnchecked(e);
        throw new CompletionException(e);
      }

      activity.end();
      return server;
    }, AppExecutorUtil.getAppExecutorService());

    log("exit: lock(): succeed");
    return new AbstractMap.SimpleEntry<>(ActivationStatus.NO_INSTANCE, null);
  }

  /**
   * <p>According to https://stackoverflow.com/a/12652718/3463676, file locks should be removed on process segfault.
   * According to {@link FileLock} documentation, file locks are marked as invalid on JVM termination.</p>
   *
   * <p>Unlocking of port files (via {@link #unlockPortFiles}) happens either after builtin server init, or on app termination.</p>
   *
   * <p>Because of that, we do not care about non-starting Netty leading to infinite lock handling, as the IDE is not ready to
   * accept connections anyway; on app termination the locks will be released.</p>
   */
  private synchronized void lockPortFiles() throws IOException {
    if (!myLockedFiles.isEmpty()) {
      throw new IllegalStateException("File locking must not be called twice");
    }

    OpenOption[] options = {StandardOpenOption.CREATE, StandardOpenOption.APPEND};
    Files.createDirectories(myConfigPath);
    FileChannel cc = FileChannel.open(myConfigPath.resolve(PORT_LOCK_FILE), options);
    myLockedFiles.add(cc);
    myLockedFiles.add(cc.lock());
    Files.createDirectories(mySystemPath);
    FileChannel sc = FileChannel.open(mySystemPath.resolve(PORT_LOCK_FILE), options);
    myLockedFiles.add(sc);
    myLockedFiles.add(sc.lock());
  }

  private synchronized void unlockPortFiles() throws Exception {
    if (myLockedFiles.isEmpty()) {
      throw new IllegalStateException("File unlocking must not be called twice");
    }
    for (int i = myLockedFiles.size() - 1; i >= 0; i--) {
      myLockedFiles.get(i).close();
    }
    myLockedFiles.clear();
  }

  private static @NotNull String readOneLine(@NotNull Path file) throws IOException {
    try (BufferedReader reader = Files.newBufferedReader(file)) {
      return reader.readLine().trim();
    }
  }

  private static void readPort(@NotNull Path dir, @NotNull Map<Integer, List<String>> portToPath) {
    try {
      portToPath.computeIfAbsent(Integer.parseInt(readOneLine(dir.resolve(PORT_FILE))), it -> new ArrayList<>()).add(dir.toString());
    }
    catch (NoSuchFileException ignore) {
    }
    catch (Exception e) {
      // no need to delete - it would be overwritten
      log(e);
    }
  }

  private @NotNull Map.Entry<ActivationStatus, CliResult> tryActivate(int portNumber, @NotNull List<String> paths, String[] args) {
    log("trying: port=%s", portNumber);

    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), portNumber)) {
      socket.setSoTimeout(5000);

      DataInput in = new DataInputStream(socket.getInputStream());
      List<String> stringList = readStringSequence(in);
      // backward compatibility: requires at least one path to match
      boolean result = ContainerUtil.intersects(paths, stringList);
      if (result) {
        // update property right now, without scheduling to EDT - in some cases, allows to avoid a splash flickering
        System.setProperty(CommandLineArgs.NO_SPLASH, "true");
        EventQueue.invokeLater(() -> {
          Runnable hideSplashTask = SplashManager.getHideTask();
          if (hideSplashTask != null) hideSplashTask.run();
        });

        try {
          String token = readOneLine(mySystemPath.resolve(TOKEN_FILE));
          @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataOutputStream out = new DataOutputStream(socket.getOutputStream());

          String currentDirectory = System.getenv(LAUNCHER_INITIAL_DIRECTORY_ENV_VAR);
          if (currentDirectory == null) {
            currentDirectory = ".";
          }
          out.writeUTF(ACTIVATE_COMMAND + token + '\0' + Paths.get(currentDirectory).toAbsolutePath().toString() + '\0' + String.join("\0", args));
          out.flush();

          socket.setSoTimeout(0);
          List<String> response = readStringSequence(in);
          log("read: response=%s", String.join(";", response));
          if (OK_RESPONSE.equals(ContainerUtil.getFirstItem(response))) {
            if (JetBrainsProtocolHandler.isShutdownCommand()) {
              printPID(portNumber);
            }
            return new AbstractMap.SimpleEntry<>(ActivationStatus.ACTIVATED, mapResponseToCliResult(response));
          }
        }
        catch (IOException | IllegalArgumentException e) {
          log(e);
        }

        return new AbstractMap.SimpleEntry<>(ActivationStatus.CANNOT_ACTIVATE, null);
      }
    }
    catch (ConnectException e) {
      log("%s (stale port file?)", e.getMessage());
    }
    catch (IOException e) {
      log(e);
    }

    return new AbstractMap.SimpleEntry<>(ActivationStatus.NO_INSTANCE, null);
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
        }
        catch (IOException e) {
          break;
        }
      }
    }
    catch (Exception ignore) { }
  }

  private static final class MyChannelInboundHandler extends MessageDecoder {
    private enum State {HEADER, CONTENT}

    private final List<String> myLockedPaths;
    private final AtomicReference<Function<List<String>, Future<CliResult>>> myCommandProcessorRef;
    private final String myToken;
    private State myState = State.HEADER;

    MyChannelInboundHandler(Path[] lockedPaths, AtomicReference<Function<List<String>, Future<CliResult>>> commandProcessorRef, String token) {
      myLockedPaths = new ArrayList<>(lockedPaths.length);
      for (Path path : lockedPaths) {
        myLockedPaths.add(path.toString());
      }
      myCommandProcessorRef = commandProcessorRef;
      myToken = token;
    }

    @Override
    public void channelActive(@NotNull ChannelHandlerContext context) throws Exception {
      sendStringSequence(context, myLockedPaths);
    }

    @Override
    protected void messageReceived(@NotNull ChannelHandlerContext context, @NotNull ByteBuf input) throws Exception {
      while (true) {
        switch (myState) {
          case HEADER: {
            ByteBuf buffer = getBufferIfSufficient(input, 2, context);
            if (buffer == null) return;
            contentLength = buffer.readUnsignedShort();
            if (contentLength > 8192) {
              context.close();
              return;
            }
            myState = State.CONTENT;
            break;
          }

          case CONTENT: {
            CharSequence command = readChars(input);
            if (command == null) {
              return;
            }

            if (StringUtil.startsWith(command, PID_COMMAND)) {
              ByteBuf buffer = context.alloc().ioBuffer();
              try (ByteBufOutputStream out = new ByteBufOutputStream(buffer)) {
                String name = ManagementFactory.getRuntimeMXBean().getName();
                out.writeUTF(name);
              }
              context.writeAndFlush(buffer);
            }

            if (StringUtil.startsWith(command, ACTIVATE_COMMAND)) {
              String data = command.subSequence(ACTIVATE_COMMAND.length(), command.length()).toString();
              List<String> args = StringUtil.split(data, data.contains("\0") ? "\0" : "\uFFFD");

              CliResult result;
              boolean tokenOK = !args.isEmpty() && myToken.equals(args.get(0));
              if (!tokenOK) {
                log(new UnsupportedOperationException("unauthorized request: " + command));
                Notifications.Bus.notify(new Notification(
                  Notifications.SYSTEM_MESSAGES_GROUP_ID,
                  IdeBundle.message("activation.auth.title"),
                  IdeBundle.message("activation.auth.message"),
                  NotificationType.WARNING));
                result = new CliResult(Main.ACTIVATE_WRONG_TOKEN_CODE, IdeBundle.message("activation.auth.message"));
              }
              else {
                Future<CliResult> future = myCommandProcessorRef.get().apply(args.subList(1, args.size()));
                result = CliResult.unmap(future, Main.ACTIVATE_ERROR);
              }

              List<String> response = new ArrayList<>();
              ContainerUtil.addAllNotNull(response, OK_RESPONSE, String.valueOf(result.exitCode), result.message);
              sendStringSequence(context, response);
            }
            context.close();
            break;
          }
        }
      }
    }
  }

  private static void sendStringSequence(@NotNull ChannelHandlerContext context, @NotNull List<String> strings) throws IOException {
    ByteBuf buffer = context.alloc().ioBuffer(1024);
    boolean success = false;
    try (ByteBufOutputStream out = new ByteBufOutputStream(buffer)) {
      for (String s : strings) {
        out.writeUTF(s);
      }
      out.writeUTF(PATHS_EOT_RESPONSE);
      success = true;
    }
    finally {
      if (!success) {
        buffer.release();
      }
    }
    context.writeAndFlush(buffer);
  }

  private static @NotNull List<String> readStringSequence(@NotNull DataInput in) {
    List<String> result = new ArrayList<>();
    while (true) {
      try {
        String string = in.readUTF();
        log("read: path=%s", string);
        if (PATHS_EOT_RESPONSE.equals(string)) {
          break;
        }
        result.add(string);
      }
      catch (IOException e) {
        log("read: %s", e.getMessage());
        break;
      }
    }
    return result;
  }

  private static @NotNull CliResult mapResponseToCliResult(@NotNull List<String> responseParts) throws IllegalArgumentException {
    if (responseParts.size() > 3 || responseParts.size() < 2) {
      throw new IllegalArgumentException("bad response: " + String.join(";", responseParts));
    }

    int code;
    try {
      code = Integer.parseInt(responseParts.get(1));
    }
    catch (NumberFormatException e) {
      throw new IllegalArgumentException("Second part is not a parsable return code", e);
    }

    String message = responseParts.size() == 3 ? responseParts.get(2) : null;
    return new CliResult(code, message);
  }

  private static void log(Exception e) {
    Logger.getInstance(SocketLock.class).warn(e);
  }

  private static void log(@NonNls String format, Object... args) {
    Logger logger = Logger.getInstance(SocketLock.class);
    if (logger.isDebugEnabled()) {
      logger.debug(String.format(format, args));
    }
  }
}