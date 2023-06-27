// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.BootstrapBundle;
import com.intellij.ide.CliResult;
import com.intellij.ide.SpecialConfigFiles;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.Suppressions;
import com.intellij.util.User32Ex;
import com.sun.jna.platform.win32.WinDef;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static java.util.Objects.requireNonNullElse;

/**
 * The class ensures that only one IDE instance is running on the given pair of configuration/cache directories,
 * and participates in the CLI by passing arguments and relaying back exit codes and error messages.
 */
final class DirectoryLock {
  static final class CannotActivateException extends Exception {
    private CannotActivateException(Throwable cause) {
      super(cause);
    }

    @Override
    public @Nls String getMessage() {
      return BootstrapBundle.message("bootstrap.error.cannot.activate.message", getCause().getClass().getSimpleName(), getCause().getMessage());
    }
  }

  private static final int UDS_PATH_LENGTH_LIMIT = 100;
  private static final int LOCK_RETRIES = 3;
  private static final int BUFFER_LENGTH = 16_384;
  private static final int MARKER = 0xFACADE;
  private static final int HEADER_LENGTH = 6;  // the marker (4 bytes) + a packet length (2 bytes)
  private static final String SERVER_THREAD_NAME = "External Command Listener";

  private static final Logger LOG = getLogger();
  private static final AtomicInteger COUNT = new AtomicInteger();  // to ensure redirected port file uniqueness in tests

  private static Logger getLogger() {
    Logger logger = Logger.getInstance(DirectoryLock.class);
    if (Boolean.getBoolean("ij.dir.lock.debug")) logger.setLevel(LogLevel.DEBUG);
    return logger;
  }

  private final String myPid = String.valueOf(ProcessHandle.current().pid());
  private final Path myPortFile;
  private final Path myLockFile;
  private final boolean myFallbackMode;
  private final @Nullable Path myRedirectedPortFile;
  private final Function<List<String>, CliResult> myProcessor;

  private volatile @Nullable ServerSocketChannel myServerChannel = null;

  DirectoryLock(@NotNull Path configPath, @NotNull Path systemPath, @NotNull Function<List<String>, CliResult> processor) {
    myPortFile = systemPath.resolve(SpecialConfigFiles.PORT_FILE);
    myLockFile = configPath.resolve(SpecialConfigFiles.LOCK_FILE);

    myFallbackMode = !areUdsSupported(myPortFile);

    if (LOG.isDebugEnabled()) {
      LOG.debug("portFile=" + myPortFile + " lockFile=" + myLockFile + " fallback=" + myFallbackMode);
    }

    if (!myFallbackMode && myPortFile.toString().length() > UDS_PATH_LENGTH_LIMIT) {
      var baseDir = SystemInfoRt.isWindows ? Path.of(System.getenv("SystemRoot"), "Temp") : Path.of("/tmp");
      myRedirectedPortFile = baseDir.resolve(".ij_redirected_port_" + myPid + "_" + COUNT.incrementAndGet());
      if (LOG.isDebugEnabled()) LOG.debug("redirectedPortFile=" + myRedirectedPortFile);
    }
    else {
      myRedirectedPortFile = null;
    }

    myProcessor = processor;
  }

  private static boolean areUdsSupported(Path file) {
    if (!SystemInfoRt.isUnix) {
      try {
        SocketChannel.open(StandardProtocolFamily.UNIX).close();
      }
      catch (UnsupportedOperationException e) {
        return false;
      }
      catch (IOException ignored) { }
    }

    return file.getFileSystem().getClass().getModule() == Object.class.getModule();
  }

  /**
   * Tries to grab a port file and start listening for incoming requests.
   * Failing that, attempts to connect via the existing port file to an already running instance.
   * Returns {@code null} on successfully locking the directories, a non-null value on successfully activating another instance,
   * or throws a {@link CannotActivateException}.
   */
  @Nullable CliResult lockOrActivate(@NotNull Path currentDirectory, @NotNull List<String> args) throws CannotActivateException, IOException {
    var configDir = NioFiles.createDirectories(myLockFile.getParent());
    var systemDir = NioFiles.createDirectories(myPortFile.getParent());
    if (Files.isSameFile(systemDir, configDir)) {
      throw new IllegalArgumentException(BootstrapBundle.message("bootstrap.error.same.directories"));
    }

    try {
      return tryListen();
    }
    catch (BindException | FileAlreadyExistsException e) {
      LOG.debug(e);
    }

    try {
      return tryConnect(args, currentDirectory);
    }
    catch (SocketException e) {
      LOG.debug(e);
    }
    catch (IOException e) {
      LOG.debug(e);
      throw new CannotActivateException(e);
    }

    Files.deleteIfExists(myPortFile);
    return tryListen();
  }

  void dispose() {
    var serverChannel = myServerChannel;
    myServerChannel = null;
    if (serverChannel != null) {
      Suppressions.runSuppressing(
        () -> serverChannel.close(),
        () -> {
          if (myRedirectedPortFile != null) {
            Files.deleteIfExists(myRedirectedPortFile);
          }
        },
        () -> Files.deleteIfExists(myPortFile),
        () -> Files.deleteIfExists(myLockFile));
    }
  }

  private CliResult tryConnect(List<String> args, Path currentDirectory) throws IOException {
    try (var socketChannel = SocketChannel.open(myFallbackMode ? StandardProtocolFamily.INET : StandardProtocolFamily.UNIX)) {
      SocketAddress address;
      if (myFallbackMode) {
        var port = 0;
        try { port = Integer.parseInt(Files.readString(myPortFile)); }
        catch (NumberFormatException e) { throw new SocketException("Invalid port; " + e.getMessage()); }
        address = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), port);
      }
      else if (myRedirectedPortFile != null) {
        address = UnixDomainSocketAddress.of(Files.readString(myPortFile));
      }
      else {
        address = UnixDomainSocketAddress.of(myPortFile);
      }

      if (LOG.isDebugEnabled()) LOG.debug("connecting to " + address);
      socketChannel.connect(address);

      allowActivation();

      var request = new ArrayList<String>(args.size() + 1);
      request.add(currentDirectory.toString());
      request.addAll(args);
      sendLines(socketChannel, request);

      var response = readLines(socketChannel);
      if (response.size() != 2) throw new IOException(BootstrapBundle.message("bootstrap.error.malformed.response", response));
      var exitCode = Integer.parseInt(response.get(0));
      var message = response.get(1);
      return new CliResult(exitCode, message.isEmpty() ? null : message);
    }
  }

  private void allowActivation() {
    if (SystemInfoRt.isWindows && JnaLoader.isLoaded()) {
      try {
        var remotePID = Long.parseLong(Files.readString(myLockFile));
        User32Ex.INSTANCE.AllowSetForegroundWindow(new WinDef.DWORD(remotePID));
      }
      catch (Throwable t) {
        LOG.debug(t);
      }
    }
  }

  private @Nullable CliResult tryListen() throws IOException, CannotActivateException {
    var serverChannel = ServerSocketChannel.open(myFallbackMode ? StandardProtocolFamily.INET : StandardProtocolFamily.UNIX);

    SocketAddress address;
    if (myFallbackMode) {
      Files.writeString(myPortFile, "0", StandardOpenOption.CREATE_NEW);
      address = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 0);
    }
    else if (myRedirectedPortFile != null) {
      Files.writeString(myPortFile, myRedirectedPortFile.toString(), StandardOpenOption.CREATE_NEW);
      address = UnixDomainSocketAddress.of(myRedirectedPortFile);
    }
    else {
      address = UnixDomainSocketAddress.of(myPortFile);
    }

    if (LOG.isDebugEnabled()) LOG.debug("binding to " + address);
    serverChannel.bind(address);
    myServerChannel = serverChannel;

    try {
      if (myFallbackMode) {
        var port = ((InetSocketAddress)serverChannel.getLocalAddress()).getPort();
        Files.writeString(myPortFile, String.valueOf(port), StandardOpenOption.TRUNCATE_EXISTING);
      }
      lockDirectory(myLockFile);
    }
    catch (Exception e) {
      LOG.debug(e);
      dispose();
      throw new CannotActivateException(e);
    }

    new Thread(this::acceptConnections, SERVER_THREAD_NAME).start();
    return null;
  }

  private void lockDirectory(Path lockFile) throws Exception {
    IOException first = null;

    for (var i = 0; i < LOCK_RETRIES; i++) {
      try {
        Files.writeString(lockFile, myPid, StandardOpenOption.CREATE_NEW);
        return;
      }
      catch (FileAlreadyExistsException e) {
        first = Suppressions.addSuppressed(first, e);
        try {
          try {
            var otherPid = Long.parseLong(Files.readString(lockFile));
            if (ProcessHandle.of(otherPid).isPresent()) {
              throw new Exception(BootstrapBundle.message("bootstrap.error.still.running", otherPid), e);
            }
          }
          catch (NumberFormatException ignored) { }
          Files.deleteIfExists(lockFile);
        }
        catch (IOException ex) {
          first = Suppressions.addSuppressed(first, ex);
        }
      }
    }

    throw first;
  }

  private void acceptConnections() {
    var serverChannel = myServerChannel;
    if (serverChannel == null) return;
    while (true) {
      try {
        var socketChannel = serverChannel.accept();
        ProcessIOExecutorService.INSTANCE.execute(() -> handleConnection(socketChannel));
      }
      catch (ClosedChannelException e) { break; }
      catch (IOException e) {
        LOG.warn(e);
        break;
      }
    }
  }

  private void handleConnection(SocketChannel socketChannel) {
    try (socketChannel) {
      var request = readLines(socketChannel);

      CliResult result;
      try {
        result = myProcessor.apply(request);
      }
      catch (Throwable t) {
        LOG.error(t);
        var error = requireNonNullElse(t.getMessage(), "Unknown error");
        var message = BootstrapBundle.message("bootstrap.error.request.failed", myPid, t.getClass(), error, LoggerFactory.getLogFilePath());
        result = new CliResult(AppExitCodes.ACTIVATE_ERROR, message);
      }

      sendLines(socketChannel, List.of(String.valueOf(result.exitCode()), requireNonNullElse(result.message(), "")));
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  //<editor-fold desc="Helpers">
  private static void sendLines(SocketChannel socketChannel, List<String> lines) throws IOException {
    var buffer = ByteBuffer.allocate(BUFFER_LENGTH);
    buffer.putInt(MARKER).putShort((short)0);

    for (var line : lines) {
      var bytes = line.getBytes(StandardCharsets.UTF_8);
      buffer.putShort((short)bytes.length);
      buffer.put(bytes);
    }

    buffer.putShort(4, (short)buffer.position());

    buffer.flip();
    while (buffer.hasRemaining()) {
      socketChannel.write(buffer);
    }
  }

  private static List<String> readLines(SocketChannel socketChannel) throws IOException {
    var buffer = ByteBuffer.allocate(BUFFER_LENGTH);

    while (buffer.position() < HEADER_LENGTH) {
      if (socketChannel.read(buffer) < 0) {
        throw new EOFException("Expected " + HEADER_LENGTH + " bytes, got " + buffer.position());
      }
    }
    var length = buffer.getShort(4);
    while (buffer.position() < length) {
      if (socketChannel.read(buffer) < 0) {
        throw new EOFException("Expected " + length + " bytes, got " + buffer.position());
      }
    }

    buffer.flip();
    var marker = buffer.getInt();
    if (marker != MARKER) throw new StreamCorruptedException("Invalid marker: 0x" + Integer.toHexString(marker));
    buffer.getShort();

    var lines = new ArrayList<String>();
    while (buffer.hasRemaining()) {
      length = buffer.getShort();
      var bytes = new byte[length];
      buffer.get(bytes);
      lines.add(new String(bytes, StandardCharsets.UTF_8));
    }
    return lines;
  }
  //</editor-fold>
}
