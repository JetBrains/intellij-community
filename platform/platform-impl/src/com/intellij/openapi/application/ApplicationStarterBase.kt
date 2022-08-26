// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.CliResult
import com.intellij.idea.SocketLock
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.future.asDeferred
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

/**
 * @author Konstantin Bulenkov
 */
abstract class ApplicationStarterBase protected constructor(private vararg val argsCount: Int) : ApplicationStarter {
  override val isHeadless: Boolean
    get() = false

  companion object {
    @JvmStatic
    protected fun saveAll() {
      FileDocumentManager.getInstance().saveAllDocuments()
      ApplicationManager.getApplication().saveSettings()
    }

    @JvmStatic
    protected fun saveIfNeeded(file: VirtualFile?) {
      if (file == null) return
      val document = FileDocumentManager.getInstance().getCachedDocument(file)
      if (document != null) FileDocumentManager.getInstance().saveDocument(document)
    }
  }

  override fun canProcessExternalCommandLine(): Boolean = true

  override suspend fun processExternalCommandLine(args: List<String>, currentDirectory: String?): CliResult {
    if (!checkArguments(args)) {
      val title = ApplicationBundle.message("app.command.exec.error.title", commandName)
      ApplicationManager.getApplication().invokeLater { Messages.showMessageDialog(usageMessage, title, Messages.getInformationIcon()) }
      return CliResult(1, usageMessage)
    }
    try {
      return processCommand(args, currentDirectory).asDeferred().await()
    }
    catch (e: Exception) {
      val title = ApplicationBundle.message("app.command.exec.error.title", commandName)
      val message = ApplicationBundle.message("app.command.exec.error", commandName, e.message)
      ApplicationManager.getApplication().invokeLater { Messages.showMessageDialog(message, title, Messages.getErrorIcon()) }
      return CliResult(1, message)
    }
  }

  protected open fun checkArguments(args: List<String>): Boolean {
    return Arrays.binarySearch(argsCount, args.size - 1) != -1 && commandName == args[0]
  }

  abstract val usageMessage: @NlsContexts.DialogMessage String?

  @Throws(Exception::class)
  protected abstract fun processCommand(args: List<String>, currentDirectory: String?): CompletableFuture<CliResult>

  override fun premain(args: List<String>) {
    if (!checkArguments(args)) {
      System.err.println(usageMessage)
      exitProcess(1)
    }
  }

  override fun main(args: List<String>) {
    try {
      val exitCode: Int = try {
        val currentDirectory = System.getenv(SocketLock.LAUNCHER_INITIAL_DIRECTORY_ENV_VAR)
        val commandFuture = processCommand(args, currentDirectory)
        val result = commandFuture.get()
        if (result.message != null) {
          println(result.message)
        }
        result.exitCode
      }
      finally {
        ApplicationManager.getApplication().invokeAndWait { saveAll() }
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