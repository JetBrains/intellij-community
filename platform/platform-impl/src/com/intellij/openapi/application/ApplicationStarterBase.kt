// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.configurationStore.saveSettings
import com.intellij.ide.CliResult
import com.intellij.idea.SocketLock
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

/**
 * @author Konstantin Bulenkov
 */
@Internal
abstract class ApplicationStarterBase protected constructor(private vararg val argsCount: Int) : ModernApplicationStarter() {
  override val isHeadless: Boolean
    get() = false

  companion object {
    @JvmStatic
    protected fun saveIfNeeded(file: VirtualFile?) {
      if (file == null) {
        return
      }
      val document = FileDocumentManager.getInstance().getCachedDocument(file)
      if (document != null) {
        FileDocumentManager.getInstance().saveDocument(document)
      }
    }
  }

  override fun canProcessExternalCommandLine(): Boolean = true

  override suspend fun processExternalCommandLine(args: List<String>, currentDirectory: String?): CliResult {
    @Suppress("DEPRECATION")
    val commandName = commandName
    if (!checkArguments(args)) {
      val title = ApplicationBundle.message("app.command.exec.error.title", commandName)
      ApplicationManager.getApplication().invokeLater { Messages.showMessageDialog(usageMessage, title, Messages.getInformationIcon()) }
      return CliResult(1, usageMessage)
    }
    try {
      return executeCommand(args, currentDirectory)
    }
    catch (e: Exception) {
      val title = ApplicationBundle.message("app.command.exec.error.title", commandName)
      val message = ApplicationBundle.message("app.command.exec.error", commandName, e.message)
      ApplicationManager.getApplication().invokeLater { Messages.showMessageDialog(message, title, Messages.getErrorIcon()) }
      return CliResult(1, message)
    }
  }

  protected open fun checkArguments(args: List<String>): Boolean {
    @Suppress("DEPRECATION")
    return Arrays.binarySearch(argsCount, args.size - 1) != -1 && commandName == args[0]
  }

  abstract val usageMessage: @NlsContexts.DialogMessage String?

  protected open suspend fun executeCommand(args: List<String>, currentDirectory: String?): CliResult {
    @Suppress("DEPRECATION")
    return processCommand(args, currentDirectory).asDeferred().await()
  }

  @Throws(Exception::class)
  @Deprecated("Use executeCommand")
  protected open fun processCommand(args: List<String>, currentDirectory: String?): CompletableFuture<CliResult> {
    throw AbstractMethodError()
  }

  override fun premain(args: List<String>) {
    if (!checkArguments(args)) {
      System.err.println(usageMessage)
      exitProcess(1)
    }
  }

  final override suspend fun start(args: List<String>) {
    try {
      val exitCode: Int = try {
        val currentDirectory = System.getenv(SocketLock.LAUNCHER_INITIAL_DIRECTORY_ENV_VAR)
        val result = executeCommand(args, currentDirectory)
        result.message?.let(::println)
        result.exitCode
      }
      finally {
        withContext(Dispatchers.EDT) {
          FileDocumentManager.getInstance().saveAllDocuments()
        }
        saveSettings(ApplicationManager.getApplication())
      }
      exitProcess(exitCode)
    }
    catch (e: Exception) {
      e.printStackTrace()
      exitProcess(1)
    }
    catch (t: Throwable) {
      t.printStackTrace()
      exitProcess(2)
    }
  }
}