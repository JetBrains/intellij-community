// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.net.NetUtils
import git4idea.gpg.CryptoUtils
import git4idea.i18n.GitBundle
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.KeyPair
import java.security.NoSuchAlgorithmException

@Service(Service.Level.PROJECT)
internal class PinentryService(private val cs: CoroutineScope) {

  private var serverSocket: ServerSocket? = null
  private var keyPair: KeyPair? = null

  private var passwordUiRequester: PasswordUiRequester = DefaultPasswordUiRequester()

  @TestOnly
  internal fun setUiRequester(requester: PasswordUiRequester) {
    passwordUiRequester = requester
  }

  @Synchronized
  fun startSession(): PinentryData? {
    val publicKeyStr: String?
    try {
      val pair = CryptoUtils.generateKeyPair()
      publicKeyStr = CryptoUtils.publicKeyToString(pair.public)
      if (publicKeyStr == null) {
        LOG.warn("Cannot serialize public key")
        return null
      }
      keyPair = pair
    }
    catch (e: NoSuchAlgorithmException) {
      LOG.warn("Cannot generate key pair", e)
      return null
    }
    val address = startServer() ?: return null

    return PinentryData(publicKeyStr, address)
  }

  @Synchronized
  fun stopSession() {
    stopServer()
    keyPair = null
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun startServer(): Address? {
    val port = try {
      NetUtils.findAvailableSocketPort()
    }
    catch (e: IOException) {
      LOG.warn("Cannot find available port to start", e)
      return null
    }
    val host = NetUtils.getLocalHostString()
    serverSocket = ServerSocket(port)
    cs.launch(Dispatchers.IO.limitedParallelism(1)) {

      serverSocket.use { serverSocket ->
        while (isActive) {
          try {
            val clientSocket = serverSocket?.accept()
            if (clientSocket != null) {
              launch(Dispatchers.IO) { handleClient(clientSocket) }
            }
          }
          catch (e: SocketException) {
            if (serverSocket?.isClosed == true) break

            LOG.warn("Socket exception", e)
          }
        }
      }
    }

    cs.launch {
      while (isActive) {
        delay(100L)
      }
    }.invokeOnCompletion {
      stopSession()
    }

    return Address(host, port)
  }

  private fun stopServer() {
    try {
      serverSocket?.use(ServerSocket::close)
    }
    catch (e: IOException) {
      LOG.warn("Cannot stop server", e)
    }
  }

  private suspend fun handleClient(clientConnection: Socket) {
    clientConnection.use { connection ->
      connection.getInputStream().bufferedReader().use { reader ->
        connection.getOutputStream().bufferedWriter().use { writer ->
          val requestLine = reader.readLine()
          val request = requestLine.split(' ', limit = 3)
          if (request.getOrNull(0) == "GETPIN") {
            val description = request.getOrNull(2)?.replace("%0A", "\n")?.replace("%22", "\"")
            val passphrase = withContext(Dispatchers.EDT) {
              passwordUiRequester.requestPassword(description)
            }
            val privateKey = keyPair?.private
            if (passphrase != null && privateKey != null) {
              val encryptedPassphrase = CryptoUtils.encrypt(passphrase, privateKey)
              writer.write("D $encryptedPassphrase\n")
              writer.write("OK\n")
            }
            else {
              writer.write("ERR 83886178 cancel\n")
            }
            writer.flush()
          }
        }
      }
    }
  }

  internal fun interface PasswordUiRequester {
    fun requestPassword(description: @NlsSafe String?): String?
  }

  private class DefaultPasswordUiRequester() : PasswordUiRequester {
    override fun requestPassword(description: @NlsSafe String?): String? {
      return Messages.showPasswordDialog(
        if (description != null) description else GitBundle.message("gpg.pinentry.default.description"),
        GitBundle.message("gpg.pinentry.title"),
      )
    }
  }


  data class Address(val host: String, val port: Int) {
    override fun toString(): String = "$host:$port"
  }

  data class PinentryData(val publicKey: String, val address: Address) {
    override fun toString(): String = "$publicKey:$address"
  }

  companion object {
    private val LOG = logger<PinentryService>()
    const val PINENTRY_USER_DATA_ENV = "PINENTRY_USER_DATA"
    @JvmStatic
    fun getInstance(project: Project): PinentryService = project.service()
  }
}
