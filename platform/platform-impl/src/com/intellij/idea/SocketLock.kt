// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.idea

import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.CliResult
import com.intellij.ide.IdeBundle
import com.intellij.ide.SpecialConfigFiles
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.User32Ex
import com.sun.jna.platform.win32.WinDef
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufOutputStream
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.io.BuiltInServer
import org.jetbrains.io.MessageDecoder
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private const val PATHS_EOT_RESPONSE = "---"
private const val ACTIVATE_COMMAND = "activate "
private const val OK_RESPONSE = "ok"

class SocketLock(@JvmField val configPath: Path, @JvmField val systemPath: Path) {
  companion object {
    /**
     * Name of an environment variable that will be set by the Windows launcher and will contain the working directory the
     * IDE was started with.
     *
     * This is necessary on Windows because the launcher needs to change the current directory for the JVM to load
     * properly; see the details in WindowsLauncher.cpp.
     */
    const val LAUNCHER_INITIAL_DIRECTORY_ENV_VAR = "IDEA_INITIAL_DIRECTORY"
  }

  enum class ActivationStatus {
    ACTIVATED, NO_INSTANCE, CANNOT_ACTIVATE
  }

  private val commandProcessorRef: AtomicReference<(List<String>) -> Deferred<CliResult>> = AtomicReference {
    CompletableDeferred(CliResult(AppExitCodes.ACTIVATE_NOT_INITIALIZED, IdeBundle.message("activation.not.initialized")))
  }

  private val lockedFiles = ArrayList<AutoCloseable>(4)

  @Volatile
  var serverFuture: Deferred<BuiltInServer>? = null
    private set

  init {
    if (configPath == systemPath) {
      throw IllegalArgumentException("'config' and 'system' paths should point to different directories")
    }
  }

  fun setCommandProcessor(processor: (List<String>) -> Deferred<CliResult>) {
    commandProcessorRef.set(processor)
  }

  fun dispose() {
    log("enter: dispose()")
    var server: BuiltInServer? = null
    try {
      server = getServer()
    }
    catch (ignored: Exception) {
    }

    try {
      if (lockedFiles.isEmpty()) {
        lockPortFiles()
      }
      server?.let {
        Disposer.dispose(it)
      }
      Files.deleteIfExists(configPath.resolve(SpecialConfigFiles.PORT_FILE))
      Files.deleteIfExists(systemPath.resolve(SpecialConfigFiles.PORT_FILE))
      Files.deleteIfExists(systemPath.resolve(SpecialConfigFiles.TOKEN_FILE))
      unlockPortFiles()
    }
    catch (e: Exception) {
      log(e)
    }
  }

  fun getServer(): BuiltInServer? = serverFuture?.asCompletableFuture()?.join()

  fun lockAndTryActivate(args: List<String>, mainScope: CoroutineScope): Pair<ActivationStatus, CliResult?> {
    log("enter: lock(config=%s system=%s)", configPath, systemPath)
    lockPortFiles()
    val portToPath = HashMap<Int, MutableList<String>>()
    readPort(configPath, portToPath)
    readPort(systemPath, portToPath)
    if (!portToPath.isEmpty()) {
      for ((key, value) in portToPath) {
        val status = tryActivate(portNumber = key, paths = value, args = args, systemPath = systemPath)
        if (status.first != ActivationStatus.NO_INSTANCE) {
          log("exit: lock(): " + status.second)
          unlockPortFiles()
          return status
        }
      }
    }

    serverFuture = mainScope.async(Dispatchers.IO) {
      val activity = StartUpMeasurer.startActivity("built-in server launch", ActivityCategory.DEFAULT)
      val token = UUID.randomUUID().toString()
      val lockedPaths = arrayOf(configPath, systemPath)
      val server = BuiltInServer.start(firstPort = 6942, portsCount = 50, tryAnyPort = false) {
        MyChannelInboundHandler(lockedPaths = lockedPaths, commandProcessor = { commandProcessorRef.get() }, token = token)
      }
      val portBytes = server.port.toString().toByteArray(StandardCharsets.UTF_8)
      Files.write(configPath.resolve(SpecialConfigFiles.PORT_FILE), portBytes)
      Files.write(systemPath.resolve(SpecialConfigFiles.PORT_FILE), portBytes)
      val tokenFile = systemPath.resolve(SpecialConfigFiles.TOKEN_FILE)
      Files.write(tokenFile, token.toByteArray(StandardCharsets.UTF_8))
      val view = Files.getFileAttributeView(tokenFile, PosixFileAttributeView::class.java)
      if (view != null) {
        try {
          view.setPermissions(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }
        catch (e: IOException) {
          log(e)
        }
      }
      unlockPortFiles()

      activity.end()
      server
    }
    log("exit: lock(): succeed")
    return ActivationStatus.NO_INSTANCE to null
  }

  /**
   * According to [Stack Overflow](https://stackoverflow.com/a/12652718/3463676), file locks should be removed on process segfault.
   * According to [FileLock] documentation, file locks are marked as invalid on JVM termination.
   *
   * Unlocking of port files (via [.unlockPortFiles]) happens either after builtin server init, or on app termination.
   *
   * Because of that, we do not care about non-starting Netty leading to infinite lock handling, as the IDE is not ready to
   * accept connections anyway; on app termination the locks will be released.
   */
  @Suppress("KDocUnresolvedReference")
  @Synchronized
  private fun lockPortFiles() {
    check(lockedFiles.isEmpty()) { "File locking must not be called twice" }
    val options = arrayOf<OpenOption>(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    Files.createDirectories(configPath)
    val cc = FileChannel.open(configPath.resolve(SpecialConfigFiles.PORT_LOCK_FILE), *options)
    lockedFiles.add(cc)
    lockedFiles.add(cc.lock())
    Files.createDirectories(systemPath)
    val sc = FileChannel.open(systemPath.resolve(SpecialConfigFiles.PORT_LOCK_FILE), *options)
    lockedFiles.add(sc)
    lockedFiles.add(sc.lock())
  }

  @Synchronized
  private fun unlockPortFiles() {
    check(!lockedFiles.isEmpty()) { "File unlocking must not be called twice" }
    for (i in lockedFiles.indices.reversed()) {
      lockedFiles.get(i).close()
    }
    lockedFiles.clear()
  }
}

private fun allowActivation(input: DataInputStream) {
  if (SystemInfoRt.isWindows) {
    try {
      User32Ex.INSTANCE.AllowSetForegroundWindow(WinDef.DWORD(input.readLong()))
    }
    catch (e: Exception) {
      log(e)
    }
  }
}

private fun tryActivate(portNumber: Int,
                        paths: List<String>,
                        args: List<String>,
                        systemPath: Path): Pair<SocketLock.ActivationStatus, CliResult?> {
  log("trying: port=%s", portNumber)
  try {
    Socket(InetAddress.getByName("127.0.0.1"), portNumber).use { socket ->
      socket.soTimeout = 5000
      val input = DataInputStream(socket.getInputStream())
      val stringList = readStringSequence(input)
      // backward compatibility: requires at least one path to match
      val result = paths.any(stringList::contains)
      if (result) {
        try {
          allowActivation(input)
          val token = readOneLine(systemPath.resolve(SpecialConfigFiles.TOKEN_FILE))
          val out = DataOutputStream(socket.getOutputStream())
          var currentDirectory = System.getenv(SocketLock.LAUNCHER_INITIAL_DIRECTORY_ENV_VAR)
          if (currentDirectory == null) {
            currentDirectory = "."
          }
          out.writeUTF(
            ACTIVATE_COMMAND + token + '\u0000' + Path.of(currentDirectory).toAbsolutePath() + '\u0000' + args.joinToString(separator = "\u0000"))
          out.flush()
          socket.soTimeout = 0
          val response = readStringSequence(input)
          log("read: response=%s", java.lang.String.join(";", response))
          if (!response.isEmpty() && OK_RESPONSE == response[0]) {
            return SocketLock.ActivationStatus.ACTIVATED to mapResponseToCliResult(response)
          }
        }
        catch (e: IOException) {
          log(e)
        }
        catch (e: IllegalArgumentException) {
          log(e)
        }
        return SocketLock.ActivationStatus.CANNOT_ACTIVATE to null
      }
    }
  }
  catch (e: ConnectException) {
    log("%s (stale port file?)", e.message!!)
  }
  catch (e: IOException) {
    log(e)
  }
  return SocketLock.ActivationStatus.NO_INSTANCE to null
}

private fun readOneLine(file: Path) = Files.newBufferedReader(file).use { it.readLine().trim() }

private fun readPort(dir: Path, portToPath: MutableMap<Int, MutableList<String>>) {
  try {
    portToPath.computeIfAbsent(readOneLine(dir.resolve(SpecialConfigFiles.PORT_FILE)).toInt()) { ArrayList() }.add(dir.toString())
  }
  catch (ignore: NoSuchFileException) {
  }
  catch (e: Exception) {
    // no need to delete a file, it will be overwritten
    log(e)
  }
}

private fun readStringSequence(input: DataInput): List<String> {
  val result = ArrayList<String>()
  while (true) {
    try {
      val string = input.readUTF()
      log("read: path=%s", string)
      if (PATHS_EOT_RESPONSE == string) {
        break
      }
      result.add(string)
    }
    catch (e: IOException) {
      log("read: %s", e.message!!)
      break
    }
  }
  return result
}

private fun mapResponseToCliResult(responseParts: List<String>): CliResult {
  if (responseParts.size > 3 || responseParts.size < 2) {
    throw IllegalArgumentException("bad response: " + java.lang.String.join(";", responseParts))
  }
  val code = try {
    responseParts[1].toInt()
  }
  catch (e: NumberFormatException) {
    throw IllegalArgumentException("Second part is not a parsable return code", e)
  }
  return CliResult(code, if (responseParts.size == 3) responseParts[2] else null)
}

private fun log(format: String, vararg args: Any) {
  val logger = Logger.getInstance(SocketLock::class.java)
  if (logger.isDebugEnabled) {
    logger.debug(String.format(format, *args))
  }
}

private fun sendStringSequence(context: ChannelHandlerContext, strings: List<String>) {
  val buffer = context.alloc().ioBuffer(1024)
  var success = false
  try {
    ByteBufOutputStream(buffer).use { out ->
      for (s in strings) {
        out.writeUTF(s)
      }
      out.writeUTF(PATHS_EOT_RESPONSE)
      if (SystemInfoRt.isWindows) {
        // see 'allowActivation' function
        out.writeLong(ProcessHandle.current().pid())
      }
      success = true
    }
  }
  finally {
    if (!success) {
      buffer.release()
    }
  }
  context.writeAndFlush(buffer)
}

private fun log(e: Exception) {
  Logger.getInstance(SocketLock::class.java).warn(e)
}

private class MyChannelInboundHandler(lockedPaths: Array<Path>,
                                      private val commandProcessor: () -> (List<String>) -> Deferred<CliResult>,
                                      private val token: String) : MessageDecoder() {
  private enum class State {
    HEADER, CONTENT
  }

  private val lockedPaths = lockedPaths.map(Path::toString)
  private var state = State.HEADER

  override fun channelActive(context: ChannelHandlerContext) {
    sendStringSequence(context, lockedPaths)
  }

  override fun messageReceived(context: ChannelHandlerContext, input: ByteBuf) {
    while (true) {
      when (state) {
        State.HEADER -> {
          val buffer = getBufferIfSufficient(input, 2, context) ?: return
          contentLength = buffer.readUnsignedShort()
          if (contentLength > 8192) {
            context.close()
            return
          }
          state = State.CONTENT
        }
        State.CONTENT -> {
          val command = readChars(input) ?: return
          if (command.startsWith(ACTIVATE_COMMAND)) {
            val data = command.subSequence(ACTIVATE_COMMAND.length, command.length).toString()
            val tokenizer = StringTokenizer(data, if (data.contains("\u0000")) "\u0000" else "\uFFFD")
            val tokenOK = tokenizer.hasMoreTokens() && token == tokenizer.nextToken()
            val result = if (tokenOK) {
              val list = ArrayList<String>()
              while (tokenizer.hasMoreTokens()) {
                list.add(tokenizer.nextToken())
              }

              try {
                runBlocking {
                  commandProcessor()(list).await()
                }
              }
              catch (e: Exception) {
                CliResult(AppExitCodes.ACTIVATE_ERROR, e.message)
              }
            }
            else {
              log(UnsupportedOperationException("unauthorized request: $command"))
              CliResult(AppExitCodes.ACTIVATE_WRONG_TOKEN_CODE, IdeBundle.message("activation.auth.message"))
            }

            val exitCode = result.exitCode.toString()
            val message = result.message
            @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
            val response = if (message == null) {
              java.util.List.of(OK_RESPONSE, exitCode)
            }
            else {
              java.util.List.of(OK_RESPONSE, exitCode, message)
            }
            sendStringSequence(context, response)
          }
          context.close()
        }
      }
    }
  }
}