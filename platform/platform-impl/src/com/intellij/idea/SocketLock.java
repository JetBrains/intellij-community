// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.BuiltInServer;
import org.jetbrains.io.MessageDecoder;

import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.Pair.pair;

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

  private final AtomicReference<Function<List<String>, Future<CliResult>>> myCommandProcessorRef = new AtomicReference<>();
  private final String myConfigPath;
  private final String mySystemPath;
  private final List<AutoCloseable> myLockedFiles = new ArrayList<>(4);
  private volatile CompletableFuture<BuiltInServer> myBuiltinServerFuture;

  public SocketLock(@NotNull String configPath, @NotNull String systemPath) {
    myConfigPath = canonicalPath(configPath);
    mySystemPath = canonicalPath(systemPath);
    if (FileUtil.pathsEqual(myConfigPath, mySystemPath)) {
      throw new IllegalArgumentException("'config' and 'system' paths should point to different directories");
    }
  }

  private static String canonicalPath(String path) {
    try {
      return new File(path).getCanonicalPath();
    }
    catch (IOException ignore) {
      return path;
    }
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
      FileUtil.delete(new File(myConfigPath, PORT_FILE));
      FileUtil.delete(new File(mySystemPath, PORT_FILE));
      FileUtil.delete(new File(mySystemPath, TOKEN_FILE));
      unlockPortFiles();
    }
    catch (Exception e) {
      log(e);
    }
  }

  @Nullable
  BuiltInServer getServer() {
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

  @Nullable
  CompletableFuture<BuiltInServer> getServerFuture() {
    return myBuiltinServerFuture;
  }

  @NotNull
  public Pair<ActivationStatus, CliResult> lockAndTryActivate(@NotNull String[] args) throws Exception {
    log("enter: lock(config=%s system=%s)", myConfigPath, mySystemPath);

    lockPortFiles();

    Map<Integer, List<String>> portToPath = new HashMap<>();
    readPort(myConfigPath, portToPath);
    readPort(mySystemPath, portToPath);
    if (!portToPath.isEmpty()) {
      args = JetBrainsProtocolHandler.checkForJetBrainsProtocolCommand(args);
      for (Map.Entry<Integer, List<String>> entry : portToPath.entrySet()) {
        Pair<ActivationStatus, CliResult> status = tryActivate(entry.getKey(), entry.getValue(), args);
        if (status.first != ActivationStatus.NO_INSTANCE) {
          log("exit: lock(): " + status.first);
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
      String[] lockedPaths = {myConfigPath, mySystemPath};
      Supplier<ChannelHandler> handlerSupplier = () -> new MyChannelInboundHandler(lockedPaths, myCommandProcessorRef, token);
      BuiltInServer server = BuiltInServer.startNioOrOio(BuiltInServer.getRecommendedWorkerCount(), 6942, 50, false, handlerSupplier);
      try {
        byte[] portBytes = Integer.toString(server.getPort()).getBytes(StandardCharsets.UTF_8);
        Files.write(Paths.get(myConfigPath, PORT_FILE), portBytes);
        Files.write(Paths.get(mySystemPath, PORT_FILE), portBytes);

        Path tokenFile = Paths.get(mySystemPath, TOKEN_FILE);
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
        ExceptionUtil.rethrow(e);
      }

      activity.end();
      return server;
    }, AppExecutorUtil.getAppExecutorService());

    log("exit: lock(): succeed");
    return pair(ActivationStatus.NO_INSTANCE, null);
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
    FileUtil.createDirectory(new File(myConfigPath));
    FileChannel cc = FileChannel.open(Paths.get(myConfigPath, PORT_LOCK_FILE), options);
    myLockedFiles.add(cc);
    myLockedFiles.add(cc.lock());
    FileUtil.createDirectory(new File(mySystemPath));
    FileChannel sc = FileChannel.open(Paths.get(mySystemPath, PORT_LOCK_FILE), options);
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

  private static void readPort(@NotNull String path, @NotNull Map<Integer, List<String>> portToPath) {
    File portFile = new File(path, PORT_FILE);
    if (!portFile.exists()) {
      return;
    }

    try {
      ContainerUtilRt.putValue(Integer.parseInt(FileUtil.loadFile(portFile)), path, portToPath);
    }
    catch (Exception e) {
      log(e);  // no need to delete - it would be overwritten
    }
  }

  private Pair<ActivationStatus, CliResult> tryActivate(int portNumber, Collection<String> paths, String[] args) {
    log("trying: port=%s", portNumber);

    try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), portNumber)) {
      socket.setSoTimeout(5000);

      DataInputStream in = new DataInputStream(socket.getInputStream());
      List<String> stringList = readStringSequence(in);
      // backward compatibility: requires at least one path to match
      boolean result = ContainerUtil.intersects(paths, stringList);

      if (result) {
        // update property right now, without scheduling to EDT - in some cases, allows to avoid a splash flickering
        System.setProperty(SplashManager.NO_SPLASH, "true");
        EventQueue.invokeLater(() -> {
          Runnable hideSplashTask = SplashManager.getHideTask();
          if (hideSplashTask != null) hideSplashTask.run();
        });

        try {
          String token = FileUtil.loadFile(new File(mySystemPath, TOKEN_FILE));
          @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataOutputStream out = new DataOutputStream(socket.getOutputStream());

          String currentDirectory = System.getenv(LAUNCHER_INITIAL_DIRECTORY_ENV_VAR);
          log(LAUNCHER_INITIAL_DIRECTORY_ENV_VAR + ": " + currentDirectory);
          if (currentDirectory == null)
            currentDirectory = ".";

          out.writeUTF(ACTIVATE_COMMAND + token + '\0' + new File(currentDirectory).getAbsolutePath() + '\0' + StringUtil.join(args, "\0"));
          out.flush();

          socket.setSoTimeout(0);
          List<String> response = readStringSequence(in);
          log("read: response=%s", StringUtil.join(response, ";"));
          if (OK_RESPONSE.equals(ContainerUtil.getFirstItem(response))) {
            if (JetBrainsProtocolHandler.isShutdownCommand()) {
              printPID(portNumber);
            }
            return pair(ActivationStatus.ACTIVATED, mapResponseToCliResult(response));
          }
        }
        catch (IOException | IllegalArgumentException e) {
          log(e);
        }

        return pair(ActivationStatus.CANNOT_ACTIVATE, null);
      }
    }
    catch (ConnectException e) {
      log("%s (stale port file?)", e.getMessage());
    }
    catch (IOException e) {
      log(e);
    }

    return pair(ActivationStatus.NO_INSTANCE, null);
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

    private final String[] myLockedPaths;
    private final AtomicReference<Function<List<String>, Future<CliResult>>> myCommandProcessorRef;
    private final String myToken;
    private State myState = State.HEADER;

    MyChannelInboundHandler(@NotNull String[] lockedPaths,
                            @NotNull AtomicReference<Function<List<String>, Future<CliResult>>> commandProcessorRef,
                            @NotNull String token) {
      myLockedPaths = lockedPaths;
      myCommandProcessorRef = commandProcessorRef;
      myToken = token;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
      sendStringSequence(context, Arrays.asList(myLockedPaths));
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
                Function<List<String>, Future<CliResult>> listener = myCommandProcessorRef.get();
                if (listener != null) {
                  result = CliResult.unmap(listener.apply(args.subList(1, args.size())), Main.ACTIVATE_RESPONSE_TIMEOUT);
                }
                else {
                  result = new CliResult(Main.ACTIVATE_LISTENER_NOT_INITIALIZED, IdeBundle.message("activation.not.initialized"));
                }
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

  private static void sendStringSequence(ChannelHandlerContext context, List<String> strings) throws IOException {
    ByteBuf buffer = context.alloc().ioBuffer(1024);
    boolean success = false;
    try (ByteBufOutputStream out = new ByteBufOutputStream(buffer)) {
      for (String s : strings) out.writeUTF(s);
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

  private static List<String> readStringSequence(DataInputStream in) {
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

  private static CliResult mapResponseToCliResult(List<String> responseParts) throws IllegalArgumentException {
    if (responseParts.size() > 3 || responseParts.size() < 2) {
      throw new IllegalArgumentException("bad response: " + StringUtil.join(responseParts, ";"));
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

  private static void log(String format, Object... args) {
    Logger logger = Logger.getInstance(SocketLock.class);
    if (logger.isDebugEnabled()) {
      logger.debug(String.format(format, args));
    }
  }
}