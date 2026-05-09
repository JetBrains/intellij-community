// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.ide

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.sun.jna.platform.unix.LibC
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.BindException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.CancelledKeyException
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.ClosedChannelException
import java.nio.channels.ClosedSelectorException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<CodexIdeContextIpcTransport>()
private const val MAX_REQUEST_FRAME_BYTES: Int = 64 * 1024
private const val MAX_RESPONSE_FRAME_BYTES: Int = 8 * 1024 * 1024
private const val MAX_ACTIVE_CLIENTS: Int = 512
private const val MAX_CONCURRENT_REQUESTS: Int = 16

internal data class CodexIdeContextIpcTransportSettings(
  @JvmField val maxActiveClients: Int = MAX_ACTIVE_CLIENTS,
  @JvmField val maxRequestFrameBytes: Int = MAX_REQUEST_FRAME_BYTES,
  @JvmField val maxResponseFrameBytes: Int = MAX_RESPONSE_FRAME_BYTES,
  @JvmField val maxConcurrentRequests: Int = MAX_CONCURRENT_REQUESTS,
  val readTimeout: Duration = 5.seconds,
  val writeTimeout: Duration = 5.seconds,
)

internal interface CodexIdeContextIpcTransport : AutoCloseable {
  suspend fun serve(protocol: CodexIdeContextIpcProtocol)
}

internal fun createCodexIdeContextIpcTransport(): CodexIdeContextIpcTransport {
  return if (SystemInfoRt.isWindows) {
    WindowsCodexIdeContextIpcTransport()
  }
  else {
    UnixCodexIdeContextIpcTransport()
  }
}

internal class CodexIdeContextIpcAddressInUseException(message: String) : IOException(message)

internal class CodexIdeContextIpcTransportConnectionHandler(
  private val settings: CodexIdeContextIpcTransportSettings = CodexIdeContextIpcTransportSettings(),
) {
  suspend fun handle(
    input: InputStream,
    output: OutputStream,
    protocol: CodexIdeContextIpcProtocol,
    closeConnection: () -> Unit,
  ) {
    while (true) {
      currentCoroutineContext().ensureActive()
      val payload = readFrame(input, closeConnection) ?: return
      val response = protocol.handlePayload(payload) ?: continue
      writeFrame(output, response, closeConnection)
    }
  }

  private suspend fun readFrame(input: InputStream, closeConnection: () -> Unit): ByteArray? {
    val lengthBytes = ByteArray(Int.SIZE_BYTES)
    val headerRead = withIoTimeout(settings.readTimeout, closeConnection) {
      readExactlyOrEof(input, lengthBytes)
    }
    if (!headerRead) {
      return null
    }
    val length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).int
    if (length !in 0..settings.maxRequestFrameBytes) {
      throw IOException("Codex IDE context IPC frame exceeds maximum size")
    }
    val payload = ByteArray(length)
    withIoTimeout(settings.readTimeout, closeConnection) {
      readExactly(input, payload)
    }
    return payload
  }

  private suspend fun writeFrame(output: OutputStream, payload: ByteArray, closeConnection: () -> Unit) {
    if (payload.size > settings.maxResponseFrameBytes) {
      throw IOException("Codex IDE context IPC response exceeds maximum size")
    }
    val lengthBytes = ByteBuffer.allocate(Int.SIZE_BYTES)
      .order(ByteOrder.LITTLE_ENDIAN)
      .putInt(payload.size)
      .array()
    withIoTimeout(settings.writeTimeout, closeConnection) {
      output.write(lengthBytes)
      output.write(payload)
      output.flush()
    }
  }

  private suspend fun <T> withIoTimeout(timeout: Duration, closeConnection: () -> Unit, action: () -> T): T {
    if (timeout.inWholeMilliseconds <= 0) {
      return action()
    }
    return supervisorScope {
      val timeoutReached = AtomicBoolean(false)
      val timeoutJob = launch {
        delay(timeout)
        timeoutReached.set(true)
        closeConnection()
      }
      try {
        action()
      }
      catch (e: IOException) {
        if (timeoutReached.get()) {
          LOG.debug(e) { "Codex IDE context IPC connection timed out" }
        }
        throw e
      }
      finally {
        timeoutJob.cancel()
      }
    }
  }

  private fun readExactlyOrEof(input: InputStream, buffer: ByteArray): Boolean {
    var offset = 0
    while (offset < buffer.size) {
      val bytesRead = input.read(buffer, offset, buffer.size - offset)
      if (bytesRead < 0) {
        if (offset == 0) {
          return false
        }
        throw EOFException("Codex IDE context IPC frame ended before header was complete")
      }
      offset += bytesRead
    }
    return true
  }

  private fun readExactly(input: InputStream, buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
      val bytesRead = input.read(buffer, offset, buffer.size - offset)
      if (bytesRead < 0) {
        throw EOFException("Codex IDE context IPC frame ended before payload was complete")
      }
      offset += bytesRead
    }
  }
}

internal class UnixCodexIdeContextIpcTransport(
  private val socketPath: Path = defaultUnixSocketPath(),
  private val settings: CodexIdeContextIpcTransportSettings = CodexIdeContextIpcTransportSettings(),
) : CodexIdeContextIpcTransport {
  @Volatile
  private var serverChannel: ServerSocketChannel? = null
  @Volatile
  private var selector: Selector? = null
  @Volatile
  private var ownsSocketPath: Boolean = false
  @Volatile
  private var closed: Boolean = false
  private val activeClients = ConcurrentHashMap.newKeySet<SocketChannel>()
  private val selectorActions = ConcurrentLinkedQueue<() -> Unit>()
  private val requestSemaphore = Semaphore(settings.maxConcurrentRequests)

  override suspend fun serve(protocol: CodexIdeContextIpcProtocol) = withContext(Dispatchers.IO) {
    closed = false
    val server = bindServerSocket()
    serverChannel = server
    var openedSelector: Selector? = null
    try {
      server.configureBlocking(false)
      openedSelector = Selector.open()
      selector = openedSelector
      server.register(openedSelector, SelectionKey.OP_ACCEPT)
      supervisorScope {
        runSelectorLoop(openedSelector, server, protocol)
      }
    }
    finally {
      selector = null
      serverChannel = null
      closeActiveClients()
      closeIgnoringErrors(openedSelector)
      closeIgnoringErrors(server)
      deleteOwnedSocketPath()
    }
  }

  override fun close() {
    closed = true
    selector?.wakeup()
  }

  private suspend fun CoroutineScope.runSelectorLoop(selector: Selector, server: ServerSocketChannel, protocol: CodexIdeContextIpcProtocol) {
    while (true) {
      currentCoroutineContext().ensureActive()
      if (closed) {
        return
      }
      runSelectorActions()
      try {
        closeExpiredClients(selector)
        val timeoutMillis = nextSelectorTimeoutMillis(selector)
        runInterruptible {
          if (timeoutMillis == null) {
            selector.select()
          }
          else {
            selector.select(timeoutMillis)
          }
        }
      }
      catch (_: ClosedSelectorException) {
        return
      }
      catch (_: ClosedChannelException) {
        return
      }
      if (closed) {
        return
      }

      runSelectorActions()
      val selectedKeys = try {
        selector.selectedKeys().iterator()
      }
      catch (_: ClosedSelectorException) {
        return
      }
      while (selectedKeys.hasNext()) {
        if (closed) {
          return
        }
        val key = selectedKeys.next()
        selectedKeys.remove()
        if (!key.isValid) {
          continue
        }
        try {
          if (key.isAcceptable) {
            try {
              acceptClients(selector, server)
            }
            catch (_: ClosedSelectorException) {
              return
            }
            catch (_: ClosedChannelException) {
              return
            }
            catch (e: IOException) {
              LOG.warn("Codex IDE context IPC server accept failed", e)
              return
            }
          }
          else {
            val state = key.attachment() as? UnixClientState ?: continue
            if (key.isReadable) {
              readClient(key, state, protocol)
            }
            if (key.isValid && key.isWritable) {
              writeClient(key, state)
            }
          }
        }
        catch (_: CancelledKeyException) {
        }
        catch (e: EOFException) {
          LOG.debug(e) { "Codex IDE context IPC client closed before a frame was complete" }
          closeClient(key)
        }
        catch (e: ClosedChannelException) {
          LOG.debug(e) { "Codex IDE context IPC client channel closed" }
          closeClient(key)
        }
        catch (e: IOException) {
          LOG.debug(e) { "Codex IDE context IPC client failed" }
          closeClient(key)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.warn("Codex IDE context IPC client failed unexpectedly", e)
          closeClient(key)
        }
      }
    }
  }

  private fun acceptClients(selector: Selector, server: ServerSocketChannel) {
    while (true) {
      val client = server.accept() ?: return
      try {
        if (activeClients.size >= settings.maxActiveClients) {
          closeIgnoringErrors(client)
          continue
        }
        client.configureBlocking(false)
        activeClients.add(client)
        val state = UnixClientState(client, settings)
        client.register(selector, SelectionKey.OP_READ, state)
        LOG.debug { "Accepted Codex IDE context IPC client" }
      }
      catch (e: ClosedSelectorException) {
        activeClients.remove(client)
        closeIgnoringErrors(client)
        throw e
      }
      catch (e: IOException) {
        activeClients.remove(client)
        closeIgnoringErrors(client)
        LOG.debug(e) { "Failed to accept Codex IDE context IPC client" }
      }
      catch (e: Throwable) {
        activeClients.remove(client)
        closeIgnoringErrors(client)
        throw e
      }
    }
  }

  private fun CoroutineScope.readClient(key: SelectionKey, state: UnixClientState, protocol: CodexIdeContextIpcProtocol) {
    val payload = state.readFrameOrNull() ?: return
    state.processing = true
    updateInterestOps(key, state)
    launch(CoroutineName("Codex IDE context IPC request")) {
      var response: ByteArray? = null
      var failure: Throwable? = null
      try {
        response = requestSemaphore.withPermit {
          protocol.handlePayload(payload)
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        failure = e
      }
      postSelectorAction {
        if (!key.isValid) {
          return@postSelectorAction
        }
        state.processing = false
        if (failure != null) {
          LOG.warn("Codex IDE context IPC client request failed unexpectedly", failure)
          closeClient(key)
          return@postSelectorAction
        }
        val clientResponse = response
        if (clientResponse != null && !state.enqueueResponse(clientResponse)) {
          LOG.debug { "Codex IDE context IPC response exceeds maximum size" }
          closeClient(key)
          return@postSelectorAction
        }
        updateInterestOps(key, state)
      }
    }
  }

  private fun writeClient(key: SelectionKey, state: UnixClientState) {
    while (true) {
      val buffer = state.responseBuffers.peekFirst() ?: break
      state.ensureWriteDeadline()
      val bytesWritten = state.channel.write(buffer)
      if (bytesWritten < 0) {
        throw EOFException("Codex IDE context IPC client closed before response was complete")
      }
      if (buffer.hasRemaining()) {
        break
      }
      state.responseBuffers.removeFirst()
    }
    if (state.responseBuffers.isEmpty()) {
      state.clearWriteDeadline()
    }
    updateInterestOps(key, state)
  }

  private fun postSelectorAction(action: () -> Unit) {
    selectorActions.add(action)
    selector?.wakeup()
  }

  private fun runSelectorActions() {
    while (true) {
      val action = selectorActions.poll() ?: return
      try {
        action()
      }
      catch (e: Throwable) {
        LOG.warn("Codex IDE context IPC selector action failed", e)
      }
    }
  }

  private fun closeExpiredClients(selector: Selector) {
    val nowNanos = System.nanoTime()
    for (key in selector.keys().toList()) {
      val state = key.attachment() as? UnixClientState ?: continue
      if (state.isTimedOut(nowNanos)) {
        LOG.debug { "Codex IDE context IPC client timed out" }
        closeClient(key)
      }
    }
  }

  private fun nextSelectorTimeoutMillis(selector: Selector): Long? {
    val nowNanos = System.nanoTime()
    var nearestDeadlineNanos = Long.MAX_VALUE
    for (key in selector.keys()) {
      val state = key.attachment() as? UnixClientState ?: continue
      val deadlineNanos = state.nearestDeadlineNanos()
      if (deadlineNanos in 1 until nearestDeadlineNanos) {
        nearestDeadlineNanos = deadlineNanos
      }
    }
    if (nearestDeadlineNanos == Long.MAX_VALUE) {
      return null
    }
    val remainingNanos = nearestDeadlineNanos - nowNanos
    return max(1, remainingNanos / 1_000_000)
  }

  private fun updateInterestOps(key: SelectionKey, state: UnixClientState) {
    if (!key.isValid) {
      return
    }
    var ops = 0
    if (!state.processing && state.responseBuffers.isEmpty()) {
      ops = ops or SelectionKey.OP_READ
    }
    if (state.responseBuffers.isNotEmpty()) {
      ops = ops or SelectionKey.OP_WRITE
    }
    key.interestOps(ops)
  }

  private fun closeClient(key: SelectionKey) {
    key.cancel()
    val state = key.attachment() as? UnixClientState ?: return
    activeClients.remove(state.channel)
    closeIgnoringErrors(state.channel)
  }

  private fun closeActiveClients() {
    for (client in activeClients.toList()) {
      closeIgnoringErrors(client)
    }
    activeClients.clear()
  }

  private fun deleteOwnedSocketPath() {
    if (!ownsSocketPath) {
      return
    }
    ownsSocketPath = false
    try {
      socketPath.deleteIfExists()
    }
    catch (e: IOException) {
      LOG.debug(e) { "Failed to delete Codex IDE context IPC socket $socketPath" }
    }
  }

  private fun bindServerSocket(): ServerSocketChannel {
    prepareSocketParent()
    if (Files.exists(socketPath)) {
      if (isLiveUnixSocket(socketPath)) {
        throw CodexIdeContextIpcAddressInUseException("Codex IDE context IPC socket is already in use: $socketPath")
      }
      socketPath.deleteIfExists()
    }

    val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    try {
      server.bind(UnixDomainSocketAddress.of(socketPath))
      restrictUnixSocketPermissions(socketPath)
      ownsSocketPath = true
      LOG.info("Codex IDE context IPC listening on $socketPath")
      return server
    }
    catch (e: BindException) {
      server.close()
      if (isLiveUnixSocket(socketPath)) {
        throw CodexIdeContextIpcAddressInUseException("Codex IDE context IPC socket is already in use: $socketPath")
      }
      throw e
    }
  }

  private fun prepareSocketParent() {
    val parent = socketPath.parent ?: return
    parent.createDirectories()
    restrictUnixSocketDirectoryPermissions(parent)
  }

  private fun restrictUnixSocketDirectoryPermissions(directory: Path) {
    try {
      Files.setPosixFilePermissions(
        directory,
        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
      )
    }
    catch (e: UnsupportedOperationException) {
      LOG.debug(e) { "POSIX permissions are unavailable for Codex IDE context IPC directory $directory" }
    }
    catch (e: IOException) {
      LOG.info("Failed to restrict Codex IDE context IPC directory permissions: $directory", e)
    }
  }

  private fun restrictUnixSocketPermissions(path: Path) {
    try {
      Files.setPosixFilePermissions(path, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
    }
    catch (e: UnsupportedOperationException) {
      LOG.debug(e) { "POSIX permissions are unavailable for Codex IDE context IPC socket $path" }
    }
    catch (e: IOException) {
      LOG.info("Failed to restrict Codex IDE context IPC socket permissions: $path", e)
    }
  }
}

private class UnixClientState(
  @JvmField val channel: SocketChannel,
  private val settings: CodexIdeContextIpcTransportSettings,
) {
  @JvmField val responseBuffers: ArrayDeque<ByteBuffer> = ArrayDeque()
  @JvmField var processing: Boolean = false
  private val headerBuffer = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
  private var payloadBuffer: ByteBuffer? = null
  private var partialFrameDeadlineNanos: Long = 0
  private var writeDeadlineNanos: Long = 0

  fun readFrameOrNull(): ByteArray? {
    while (true) {
      val payload = payloadBuffer
      if (payload == null) {
        val bytesRead = channel.read(headerBuffer)
        if (bytesRead < 0) {
          if (headerBuffer.position() == 0) {
            throw ClosedChannelException()
          }
          throw EOFException("Codex IDE context IPC frame ended before header was complete")
        }
        if (headerBuffer.hasRemaining()) {
          refreshPartialFrameDeadline()
          return null
        }
        headerBuffer.flip()
        val length = headerBuffer.int
        headerBuffer.clear()
        if (length !in 0..settings.maxRequestFrameBytes) {
          throw IOException("Codex IDE context IPC frame exceeds maximum size")
        }
        if (length == 0) {
          clearPartialFrameDeadlineIfIdle()
          return ByteArray(0)
        }
        payloadBuffer = ByteBuffer.allocate(length)
        refreshPartialFrameDeadline()
      }
      else {
        val bytesRead = channel.read(payload)
        if (bytesRead < 0) {
          throw EOFException("Codex IDE context IPC frame ended before payload was complete")
        }
        if (payload.hasRemaining()) {
          refreshPartialFrameDeadline()
          return null
        }
        val frame = payload.array()
        payloadBuffer = null
        clearPartialFrameDeadlineIfIdle()
        return frame
      }
    }
  }

  fun enqueueResponse(payload: ByteArray): Boolean {
    if (payload.size > settings.maxResponseFrameBytes) {
      return false
    }
    val buffer = ByteBuffer.allocate(Int.SIZE_BYTES + payload.size).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(payload.size)
    buffer.put(payload)
    buffer.flip()
    responseBuffers.add(buffer)
    ensureWriteDeadline()
    return true
  }

  fun ensureWriteDeadline() {
    if (writeDeadlineNanos == 0L && settings.writeTimeout.inWholeNanoseconds > 0) {
      writeDeadlineNanos = System.nanoTime() + settings.writeTimeout.inWholeNanoseconds
    }
  }

  fun clearWriteDeadline() {
    writeDeadlineNanos = 0
  }

  fun isTimedOut(nowNanos: Long): Boolean {
    return partialFrameDeadlineNanos in 1..nowNanos || writeDeadlineNanos in 1..nowNanos
  }

  fun nearestDeadlineNanos(): Long {
    return when {
      partialFrameDeadlineNanos > 0 && writeDeadlineNanos > 0 -> minOf(partialFrameDeadlineNanos, writeDeadlineNanos)
      partialFrameDeadlineNanos > 0 -> partialFrameDeadlineNanos
      else -> writeDeadlineNanos
    }
  }

  private fun refreshPartialFrameDeadline() {
    if (!hasPartialFrame()) {
      partialFrameDeadlineNanos = 0
      return
    }
    if (partialFrameDeadlineNanos == 0L && settings.readTimeout.inWholeNanoseconds > 0) {
      partialFrameDeadlineNanos = System.nanoTime() + settings.readTimeout.inWholeNanoseconds
    }
  }

  private fun clearPartialFrameDeadlineIfIdle() {
    if (!hasPartialFrame()) {
      partialFrameDeadlineNanos = 0
    }
  }

  private fun hasPartialFrame(): Boolean = headerBuffer.position() > 0 || payloadBuffer != null
}

internal class WindowsCodexIdeContextIpcTransport(
  private val pipePath: String = WINDOWS_PIPE_PATH,
  private val settings: CodexIdeContextIpcTransportSettings = CodexIdeContextIpcTransportSettings(),
  private val connectionHandler: CodexIdeContextIpcTransportConnectionHandler = CodexIdeContextIpcTransportConnectionHandler(settings),
) : CodexIdeContextIpcTransport {
  @Volatile
  private var currentPipe: WinNT.HANDLE? = null
  private val currentPipeLock = Any()

  @Volatile
  private var closed: Boolean = false
  private val activePipes = ConcurrentHashMap.newKeySet<WinNT.HANDLE>()

  override suspend fun serve(protocol: CodexIdeContextIpcProtocol) = withContext(Dispatchers.IO) {
    closed = false
    supervisorScope {
      try {
        while (true) {
          currentCoroutineContext().ensureActive()
          if (closed) {
            return@supervisorScope
          }
          val pipe = createPipe()
          publishCurrentPipe(pipe)
          try {
            connectPipe(pipe)
          }
          catch (_: ClosedByInterruptException) {
            if (takeCurrentPipe(pipe) != null) {
              closePipe(pipe)
            }
            return@supervisorScope
          }
          catch (_: ClosedChannelException) {
            if (takeCurrentPipe(pipe) != null) {
              closePipe(pipe)
            }
            return@supervisorScope
          }
          catch (e: IOException) {
            if (takeCurrentPipe(pipe) != null) {
              closePipe(pipe)
            }
            if (closed) {
              return@supervisorScope
            }
            throw e
          }
          val ownsPipe = takeCurrentPipe(pipe) != null

          if (closed) {
            if (ownsPipe) {
              closePipe(pipe)
            }
            return@supervisorScope
          }
          if (activePipes.size >= settings.maxActiveClients) {
            if (ownsPipe) {
              closePipe(pipe)
            }
            continue
          }

          activePipes.add(pipe)
          launch(CoroutineName("Codex IDE context named-pipe client")) {
            handlePipe(pipe, protocol)
          }
        }
      }
      finally {
        takeCurrentPipe()?.let(::closePipe)
        closeActivePipes()
      }
    }
  }

  override fun close() {
    closed = true
    takeCurrentPipe()?.let { pipe ->
      connectToPipe(pipePath)
      closePipe(pipe)
    }
    closeActivePipes()
  }

  private suspend fun handlePipe(pipe: WinNT.HANDLE, protocol: CodexIdeContextIpcProtocol) {
    val closeOnce = once { closeActivePipe(pipe) }
    try {
      LOG.debug { "Accepted Codex IDE context named-pipe client" }
      handleConnection(
        connectionName = "Codex IDE context named-pipe client",
        input = WindowsPipeInputStream(pipe),
        output = WindowsPipeOutputStream(pipe),
        protocol = protocol,
        connectionHandler = connectionHandler,
        closeConnection = closeOnce,
      )
    }
    finally {
      closeOnce()
    }
  }

  private fun closeActivePipes() {
    for (pipe in activePipes.toList()) {
      closeActivePipe(pipe)
    }
  }

  private fun closeActivePipe(pipe: WinNT.HANDLE) {
    if (activePipes.remove(pipe)) {
      closePipe(pipe)
    }
  }

  private fun publishCurrentPipe(pipe: WinNT.HANDLE) {
    synchronized(currentPipeLock) {
      currentPipe = pipe
    }
  }

  private fun takeCurrentPipe(expected: WinNT.HANDLE? = null): WinNT.HANDLE? {
    return synchronized(currentPipeLock) {
      val pipe = currentPipe
      if (pipe == null || expected != null && pipe != expected) {
        null
      }
      else {
        currentPipe = null
        pipe
      }
    }
  }

  private fun createPipe(): WinNT.HANDLE {
    val pipe = Kernel32.INSTANCE.CreateNamedPipe(
      pipePath,
      Kernel32.PIPE_ACCESS_DUPLEX,
      Kernel32.PIPE_TYPE_BYTE or Kernel32.PIPE_READMODE_BYTE or Kernel32.PIPE_WAIT,
      WinBase.PIPE_UNLIMITED_INSTANCES,
      16384,
      16384,
      0,
      null,
    )
    if (pipe == WinNT.INVALID_HANDLE_VALUE) {
      val error = Kernel32.INSTANCE.GetLastError()
      if (error == Kernel32.ERROR_ACCESS_DENIED) {
        throw CodexIdeContextIpcAddressInUseException("Codex IDE context named pipe is already in use: $pipePath")
      }
      throw IOException("Failed to create Codex IDE context named pipe $pipePath: error=$error")
    }
    return pipe
  }

  private fun connectPipe(pipe: WinNT.HANDLE) {
    if (Kernel32.INSTANCE.ConnectNamedPipe(pipe, null)) {
      return
    }
    val error = Kernel32.INSTANCE.GetLastError()
    if (error != Kernel32.ERROR_PIPE_CONNECTED) {
      throw IOException("Failed to connect Codex IDE context named pipe $pipePath: error=$error")
    }
  }

  private fun connectToPipe(pipePath: String) {
    val handle = Kernel32.INSTANCE.CreateFile(
      pipePath,
      Kernel32.GENERIC_READ or Kernel32.GENERIC_WRITE,
      0,
      null,
      Kernel32.OPEN_EXISTING,
      0,
      null,
    )
    if (handle != WinNT.INVALID_HANDLE_VALUE) {
      Kernel32.INSTANCE.CloseHandle(handle)
    }
  }

  private fun closePipe(pipe: WinNT.HANDLE) {
    Kernel32.INSTANCE.DisconnectNamedPipe(pipe)
    Kernel32.INSTANCE.CloseHandle(pipe)
  }
}

private suspend fun handleConnection(
  connectionName: String,
  input: InputStream,
  output: OutputStream,
  protocol: CodexIdeContextIpcProtocol,
  connectionHandler: CodexIdeContextIpcTransportConnectionHandler,
  closeConnection: () -> Unit,
) {
  try {
    connectionHandler.handle(input, output, protocol, closeConnection)
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: EOFException) {
    LOG.debug(e) { "$connectionName closed before a frame was complete" }
  }
  catch (e: ClosedChannelException) {
    LOG.debug(e) { "$connectionName channel closed" }
  }
  catch (e: IOException) {
    LOG.debug(e) { "$connectionName failed" }
  }
  catch (e: Throwable) {
    LOG.warn("$connectionName failed unexpectedly", e)
  }
}

private class WindowsPipeInputStream(private val pipe: WinNT.HANDLE) : InputStream() {
  override fun read(): Int {
    val buffer = ByteArray(1)
    val bytesRead = read(buffer, 0, 1)
    return if (bytesRead < 0) -1 else buffer[0].toInt() and 0xff
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (length == 0) {
      return 0
    }
    val target = if (offset == 0) buffer else ByteArray(length)
    val bytesRead = IntByReference()
    if (!Kernel32.INSTANCE.ReadFile(pipe, target, length, bytesRead, null)) {
      return when (Kernel32.INSTANCE.GetLastError()) {
        Kernel32.ERROR_BROKEN_PIPE, Kernel32.ERROR_HANDLE_EOF -> -1
        else -> throw IOException("Failed to read Codex IDE context named pipe")
      }
    }
    if (bytesRead.value <= 0) {
      return -1
    }
    if (target !== buffer) {
      System.arraycopy(target, 0, buffer, offset, bytesRead.value)
    }
    return bytesRead.value
  }
}

private class WindowsPipeOutputStream(private val pipe: WinNT.HANDLE) : OutputStream() {
  override fun write(value: Int) {
    write(byteArrayOf(value.toByte()))
  }

  override fun write(buffer: ByteArray, offset: Int, length: Int) {
    var written = 0
    while (written < length) {
      val chunkOffset = offset + written
      val chunkLength = length - written
      val source = if (chunkOffset == 0) buffer else buffer.copyOfRange(chunkOffset, chunkOffset + chunkLength)
      val bytesWritten = IntByReference()
      if (!Kernel32.INSTANCE.WriteFile(pipe, source, chunkLength, bytesWritten, null)) {
        throw IOException("Failed to write Codex IDE context named pipe")
      }
      if (bytesWritten.value <= 0) {
        throw IOException("Failed to write Codex IDE context named pipe")
      }
      written += bytesWritten.value
    }
  }
}

private fun defaultUnixSocketPath(): Path {
  return Path.of(System.getProperty("java.io.tmpdir"))
    .resolve("codex-ipc")
    .resolve("ipc-${LibC.INSTANCE.getuid()}.sock")
}

private fun closeIgnoringErrors(closeable: AutoCloseable?) {
  try {
    closeable?.close()
  }
  catch (_: IOException) {
  }
}

private fun once(action: () -> Unit): () -> Unit {
  val invoked = AtomicBoolean(false)
  return {
    if (invoked.compareAndSet(false, true)) {
      action()
    }
  }
}

private fun isLiveUnixSocket(socketPath: Path): Boolean {
  return try {
    SocketChannel.open(StandardProtocolFamily.UNIX).use { client ->
      client.configureBlocking(false)
      client.connect(UnixDomainSocketAddress.of(socketPath))
      val deadlineNanos = System.nanoTime() + UNIX_SOCKET_LIVENESS_TIMEOUT_NANOS
      while (!client.finishConnect()) {
        if (System.nanoTime() >= deadlineNanos) {
          return false
        }
        try {
          Thread.sleep(UNIX_SOCKET_LIVENESS_POLL_MILLIS)
        }
        catch (_: InterruptedException) {
          Thread.currentThread().interrupt()
          return false
        }
      }
    }
    true
  }
  catch (_: IOException) {
    false
  }
}

private const val WINDOWS_PIPE_PATH: String = """\\.\pipe\codex-ipc"""
private const val UNIX_SOCKET_LIVENESS_TIMEOUT_NANOS: Long = 100_000_000
private const val UNIX_SOCKET_LIVENESS_POLL_MILLIS: Long = 10
