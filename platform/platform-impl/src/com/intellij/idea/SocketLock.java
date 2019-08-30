// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.CliResult;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SocketLock {
  public enum ActivateStatus {ACTIVATED, NO_INSTANCE, CANNOT_ACTIVATE}

  private static final String PATHS_EOT_RESPONSE = "---";
  private static final String PORT_FILE = "port";
  private static final String PORT_LOCK_FILE = "port.lock";
  private static final String TOKEN_FILE = "token";

  private static final String ACTIVATE_COMMAND = "activate ";
  private static final String PID_COMMAND = "pid";
  private static final String OK_RESPONSE = "ok";

  private final AtomicReference<CliRequestProcessor> myActivateListener = new AtomicReference<>();
  private final String myConfigPath;
  private final String mySystemPath;
  private final BlockingQueue<FileLock> myLockedFiles = new LinkedBlockingQueue<>();
  @Nullable private Future<? extends Disposable> myBuiltinServerFuture;
  private volatile String myToken;
  private BuiltInServer myServer;

  public SocketLock(@NotNull String configPath, @NotNull String systemPath) {
    myConfigPath = canonicalPath(configPath);
    mySystemPath = canonicalPath(systemPath);
    if (FileUtil.pathsEqual(myConfigPath, mySystemPath)) {
      throw new IllegalArgumentException("'config' and 'system' paths should point to different directories");
    }
  }

  public void setExternalInstanceListener(@Nullable CliRequestProcessor processor) {
    myActivateListener.set(processor);
  }

  public void dispose() {
    log("enter: dispose()");

    Future<? extends Disposable> builtinServerFuture = myBuiltinServerFuture;
    if (builtinServerFuture != null) {
      try {
        Disposable server = builtinServerFuture.get(5, TimeUnit.SECONDS);
        lockPortFiles();
        try {
          Disposer.dispose(server);
        }
        finally {
          myServer = null;
        }
      }
      catch (Exception e) {
        log(e);
      }
      FileUtil.delete(new File(myConfigPath, PORT_FILE));
      FileUtil.delete(new File(mySystemPath, PORT_FILE));
      FileUtil.delete(new File(mySystemPath, TOKEN_FILE));
      unlockPortFiles();
    }
  }

  @Nullable
  BuiltInServer getServer() {
    return myServer;
  }

  @NotNull
  public ActivateStatusAndResponse lockAndTryActivate() throws Exception {
    return lockAndTryActivate(ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  @NotNull
  public ActivateStatusAndResponse lockAndTryActivate(@NotNull String[] args) throws Exception {
    log("enter: lock(config=%s system=%s)", myConfigPath, mySystemPath);

    lockPortFiles();
    File portMarkerC = new File(myConfigPath, PORT_FILE);
    File portMarkerS = new File(mySystemPath, PORT_FILE);

    MultiMap<Integer, String> portToPath = MultiMap.createSmart();
    addExistingPort(portMarkerC, myConfigPath, portToPath);
    addExistingPort(portMarkerS, mySystemPath, portToPath);
    if (!portToPath.isEmpty()) {
      for (Map.Entry<Integer, Collection<String>> entry : portToPath.entrySet()) {
        ActivateStatusAndResponse status = tryActivate(entry.getKey(), entry.getValue(), args);
        if (status.getActivateStatus() != ActivateStatus.NO_INSTANCE) {
          log("exit: lock(): " + status.getActivateStatus());
          unlockPortFiles();
          return status;
        }
      }
    }

    if (isShutdownCommand()) {
      unlockPortFiles();
      System.exit(0);
    }

    Activity builtinServerLaunch = StartUpMeasurer.start("builtin server launch");
    myBuiltinServerFuture = AppExecutorUtil.getAppExecutorService().submit(() -> {
      myToken = UUID.randomUUID().toString();
      Path tokenFile = Paths.get(mySystemPath, TOKEN_FILE);
      // parent directories are already created (see underLocks)
      Files.write(tokenFile, myToken.getBytes(StandardCharsets.UTF_8));
      PosixFileAttributeView view = Files.getFileAttributeView(tokenFile, PosixFileAttributeView.class);
      if (view != null) {
        try {
          view.setPermissions(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
        catch (IOException e) {
          log(e);
        }
      }

      try {
        return launchBuiltinServer(new String[]{myConfigPath, mySystemPath});
      }
      finally {
        builtinServerLaunch.end();
      }
    });

    log("exit: lock(): succeed");
    return ActivateStatusAndResponse.emptyResponse(ActivateStatus.NO_INSTANCE);
  }

  void writePortAndUnlockFiles(int port) {
    File portMarkerC = new File(myConfigPath, PORT_FILE);
    File portMarkerS = new File(mySystemPath, PORT_FILE);
    byte[] portBytes = Integer.toString(port).getBytes(StandardCharsets.UTF_8);

    try {
      FileUtil.writeToFile(portMarkerC, portBytes);
      FileUtil.writeToFile(portMarkerS, portBytes);
    }
    catch (IOException e) {
      log(e);
    }

    unlockPortFiles();
  }

  /**
   <p> According to https://stackoverflow.com/a/12652718/3463676, file locks should be removed on process segfault.
   According to {@link FileLock} javadoc, file locks are marked as invalid on JVM termination.

   <p> Unlocking of port files (via {@link #unlockPortFiles}) happens either after builtin server init, or on app termination.

   <p> Because of that, we do not care about non-starting netty leading to infinite lock handling, as we are not ready to
   accept connections anyway; on app termination the locks will be released anyway.
   */
  private synchronized void lockPortFiles() throws IOException {
    if (!myLockedFiles.isEmpty()) {
      throw new IllegalStateException("File locking must not be called twice");
    }

    OpenOption[] options = {StandardOpenOption.CREATE, StandardOpenOption.APPEND};
    FileUtilRt.createDirectory(new File(myConfigPath));
    FileChannel cc = FileChannel.open(Paths.get(myConfigPath, PORT_LOCK_FILE), options);
    FileLock cl = cc.lock();
    myLockedFiles.add(cl);

    FileUtilRt.createDirectory(new File(mySystemPath));
    FileChannel sc = FileChannel.open(Paths.get(mySystemPath, PORT_LOCK_FILE), options);
    FileLock sl = sc.lock();
    myLockedFiles.add(sl);
  }

  private synchronized void unlockPortFiles() {
    if (myLockedFiles.isEmpty()) {
      throw new IllegalStateException("File unlocking must not be called twice");
    }

    for (FileLock lock : myLockedFiles) {
      try {
        lock.release();
      }
      catch (IOException ignore) {
      }
    }
    myLockedFiles.clear();
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
  private ActivateStatusAndResponse tryActivate(int portNumber, @NotNull Collection<String> paths, @NotNull String[] args) {
    log("trying: port=%s", portNumber);
    args = checkForJetBrainsProtocolCommand(args);
    try {
      try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), portNumber)) {
        socket.setSoTimeout(5000);

        DataInputStream in = new DataInputStream(socket.getInputStream());
        final List<String> stringList = readStringSequence(in);
        // Backward compatibility: it required at least one path to match
        boolean result = ContainerUtil.intersects(paths, stringList);

        if (result) {
          // Update property right now, without scheduling on AWT. This allows to avoid shown-and-immediately-hidden splash in some cases.
          System.setProperty(SplashManager.NO_SPLASH, "true");
          EventQueue.invokeLater(() -> {
            Runnable hideSplashTask = SplashManager.getHideTask();
            if (hideSplashTask != null) hideSplashTask.run();
          });

          try {
            String token = FileUtil.loadFile(new File(mySystemPath, TOKEN_FILE));
            @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(ACTIVATE_COMMAND + token + "\0" + new File(".").getAbsolutePath() + "\0" + StringUtil.join(args, "\0"));
            out.flush();

            socket.setSoTimeout(0);
            List<String> response = readStringSequence(in);
            log("read: response=%s", StringUtil.join(response, ";"));
            if (OK_RESPONSE.equals(ContainerUtil.getFirstItem(response))) {
              if (isShutdownCommand()) {
                printPID(portNumber);
              }
              return new ActivateStatusAndResponse(ActivateStatus.ACTIVATED, mapResponseToCliResult(response));
            }
          }
          catch (IOException | IllegalArgumentException e) {
            log(e);
          }

          return ActivateStatusAndResponse.emptyResponse(ActivateStatus.CANNOT_ACTIVATE);
        }
      }
    }
    catch (ConnectException e) {
      log("%s (stale port file?)", e.getMessage());
    }
    catch (IOException e) {
      log(e);
    }

    return ActivateStatusAndResponse.emptyResponse(ActivateStatus.NO_INSTANCE);
  }

  @Nullable
  private Disposable launchBuiltinServer(String[] lockedPaths) {
    try {
      myServer = BuiltInServer.startNioOrOio(BuiltInServer.getRecommendedWorkerCount(), 6942, 50, false, () -> {
        //noinspection CodeBlock2Expr
        // should be not inlined because handler created for each connected channel
        return new MyChannelInboundHandler(lockedPaths, myActivateListener, myToken);
      });
      writePortAndUnlockFiles(myServer.getPort());
      return myServer;
    }
    catch (Exception e) {
      log(e);
      return null;
    }
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
  @FunctionalInterface
  public interface CliRequestProcessor {
    Future<CliResult> process(@NotNull List<String> args);
  }

  private static final class MyChannelInboundHandler extends MessageDecoder {

    private enum State {HEADER, CONTENT}
    private final String[] myLockedPaths;
    private final AtomicReference<? extends CliRequestProcessor> myActivateListener;
    private final String myToken;
    private State myState = State.HEADER;

    MyChannelInboundHandler(@NotNull String[] lockedPaths,
                            @NotNull AtomicReference<? extends CliRequestProcessor> activateListener,
                            @NotNull String token) {
      myLockedPaths = lockedPaths;
      myActivateListener = activateListener;
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
              try (ByteBufOutputStream out = new ByteBufOutputStream(buffer)) {
                String name = ManagementFactory.getRuntimeMXBean().getName();
                out.writeUTF(name);
              }
              context.writeAndFlush(buffer);
            }

            if (StringUtil.startsWith(command, ACTIVATE_COMMAND)) {
              String data = command.subSequence(ACTIVATE_COMMAND.length(), command.length()).toString();
              List<String> args = StringUtil.split(data, data.contains("\0") ? "\0" : "\uFFFD");

              final CliResult result;
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
                CliRequestProcessor listener = myActivateListener.get();
                if (listener != null) {
                  result = CliResult.getOrWrapFailure(listener.process(args.subList(1, args.size())), Main.ACTIVATE_RESPONSE_TIMEOUT);
                }
                else {
                  result = new CliResult(Main.ACTIVATE_LISTENER_NOT_INITIALIZED, IdeBundle.message("activation.not.initialized"));
                }
              }

              sendStringSequence(context,
                                 Stream.of(OK_RESPONSE, String.valueOf(result.getReturnCode()), result.getMessage())
                                   .filter(Objects::nonNull)
                                   .collect(Collectors.toList()));
            }
            context.close();
          }
          break;
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

  @NotNull
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

  @NotNull
  private static CliResult mapResponseToCliResult(@NotNull List<String> responseParts) throws IllegalArgumentException {
    if (responseParts.size() > 3 || responseParts.size() < 2) {
      throw new IllegalArgumentException("bad response: " + StringUtil.join(responseParts, ";"));
    }

    final int code;
    try {
      code = Integer.parseInt(responseParts.get(1));
    }
    catch (NumberFormatException e) {
      throw new IllegalArgumentException("Second part is not a parsable return code", e);
    }

    final String message = responseParts.size() == 3 ? responseParts.get(2) : null;
    return new CliResult(code, message);
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
  public static class ActivateStatusAndResponse {
    @NotNull
    private final ActivateStatus myActivateStatus;
    @Nullable
    private final CliResult myResponse;

    public ActivateStatusAndResponse(@NotNull ActivateStatus status, @Nullable CliResult response) {
      myActivateStatus = status;
      myResponse = response;
    }
    @NotNull
    public static ActivateStatusAndResponse emptyResponse(@NotNull ActivateStatus status) {
      return new ActivateStatusAndResponse(status, null);
    }
    @NotNull
    public ActivateStatus getActivateStatus() {
      return myActivateStatus;
    }

    @Nullable
    public CliResult getResponse() {
      return myResponse;
    }
  }
}