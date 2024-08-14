// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.configurationStore.saveSettings
import com.intellij.ide.CliResult
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.bootstrap.LAUNCHER_INITIAL_DIRECTORY_ENV_VAR
import com.intellij.platform.ide.bootstrap.commandNameFromExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import kotlin.system.exitProcess

/**
 * @author Konstantin Bulenkov
 */
@Internal
abstract class ApplicationStarterBase protected constructor(private vararg val argsCount: Int) : ModernApplicationStarter() {
  abstract val usageMessage: @NlsContexts.DialogMessage String?

  override val isHeadless: Boolean
    get() = false

  companion object {
    @JvmStatic
    protected fun saveIfNeeded(file: VirtualFile?) {
      if (file == null) {
        return
      }
      FileDocumentManager.getInstance().getCachedDocument(file)?.let(FileDocumentManager.getInstance()::saveDocument)
    }
  }

  override fun canProcessExternalCommandLine(): Boolean = true

  override suspend fun processExternalCommandLine(args: List<String>, currentDirectory: String?): CliResult {
    val commandName = commandNameFromExtension
    if (!checkArguments(args)) {
      val title = ApplicationBundle.message("app.command.exec.error.title", commandName)
      withContext(Dispatchers.EDT) {
        Messages.showMessageDialog(usageMessage, title, Messages.getInformationIcon())
      }
      return CliResult(1, usageMessage)
    }

    try {
      return executeCommand(args, currentDirectory)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      e.printStackTrace() // The dialog may sometimes not be shown, e.g., in remote dev scenarios.
      val title = ApplicationBundle.message("app.command.exec.error.title", commandName)
      val message = ApplicationBundle.message("app.command.exec.error", commandName, e.message)
      withContext(Dispatchers.EDT) {
        Messages.showMessageDialog(message, title, Messages.getErrorIcon())
      }
      return CliResult(1, message)
    }
  }

  protected open fun checkArguments(args: List<String>): Boolean {
    return Arrays.binarySearch(argsCount, args.size - 1) >= 0 && commandNameFromExtension == args[0]
  }

  protected abstract suspend fun executeCommand(args: List<String>, currentDirectory: String?): CliResult

  override fun premain(args: List<String>) {
    if (!checkArguments(args)) {
      System.err.println(usageMessage)
      exitProcess(1)
    }
  }

  final override suspend fun start(args: List<String>) {
    try {
      val exitCode: Int = try {
        val result = executeCommand(args = args, currentDirectory = System.getenv(LAUNCHER_INITIAL_DIRECTORY_ENV_VAR))
        result.message?.let(::println)
        result.exitCode
      }
      finally {
        withContext(Dispatchers.EDT) {
          writeIntentReadAction {
            FileDocumentManager.getInstance().saveAllDocuments()
          }
        }
        saveSettings(ApplicationManager.getApplication())
      }
      exitProcess(exitCode)
    }
    catch (e: CancellationException) {
      throw e
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
