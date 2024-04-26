// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.impl

import com.intellij.concurrency.resetThreadContext
import com.intellij.core.CoreFileTypeRegistry
import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.command.CommandProcessorEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.openapi.vfs.impl.jar.CoreJarFileSystem
import com.intellij.openapi.vfs.local.CoreLocalFileSystem
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.UIBundle
import com.intellij.util.UriUtil
import java.awt.Component
import java.awt.FileDialog
import java.awt.KeyboardFocusManager
import java.io.File
import java.io.FilenameFilter
import java.nio.file.FileSystems
import java.nio.file.Path

internal class FileChooserDialogHelper(private val descriptor: FileChooserDescriptor) : FilenameFilter {
  @Suppress("SpellCheckingInspection")
  private val ZIP_FS_TYPE = "zipfs"

  private val localFs = lazy<VirtualFileSystem> {
    ApplicationManager.getApplication()?.let { StandardFileSystems.local() } ?: CoreLocalFileSystem()
  }
  private val jarFs = lazy<VirtualFileSystem> {
    ApplicationManager.getApplication()?.let { StandardFileSystems.jar() } ?: CoreJarFileSystem()
  }

  init {
    if (!FileTypeRegistry.isInstanceSupplierSet()) {
      val registry = CoreFileTypeRegistry()
      registry.registerFileType(ArchiveFileType.INSTANCE, "zip")
      registry.registerFileType(ArchiveFileType.INSTANCE, "jar")
      FileTypeRegistry.setInstanceSupplier { registry }
    }
  }

  fun setNativeDialogProperties() {
    if (SystemInfo.isWindows) {
      System.setProperty("sun.awt.windows.useCommonItemDialog", "true")
    }
    else if (SystemInfo.isMac) {
      var key = "awt.file.dialog.enable.filter"
      System.setProperty(key, Registry.`is`(key, true).toString())
    }
  }

  fun showNativeDialog(fileDialog: FileDialog) {
    fileDialog.filenameFilter = this

    val commandProcessor = if (ApplicationManager.getApplication() != null) CommandProcessorEx.getInstance() as CommandProcessorEx else null
    if (commandProcessor != null) {
      commandProcessor.enterModal()
      LaterInvocator.enterModal(fileDialog)
    }
    val previousFocusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    try {
      resetThreadContext().use {
        fileDialog.isVisible = true
      }
    }
    finally {
      if (commandProcessor != null) {
        commandProcessor.leaveModal()
        LaterInvocator.leaveModal(fileDialog)
        if (previousFocusOwner != null) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown { previousFocusOwner.requestFocus() }
        }
      }
    }
  }

  fun selectedFiles(paths: List<Path>, parent: Component?, title: @NlsContexts.DialogTitle String): Array<VirtualFile> {
    val owner = if (parent != null) ModalTaskOwner.component(parent) else ModalTaskOwner.guess()
    val (results, misses) = runWithModalProgressBlocking(owner, UIBundle.message("file.chooser.vfs.progress")) {
      val results = mutableListOf<VirtualFile>()
      val misses = mutableListOf<@NlsSafe String>()
      for (path in paths) {
        val file = toVirtualFile(path)
        val adjusted = if (file != null && file.isValid()) descriptor.getFileToSelect(file) else null
        if (adjusted != null) {
          results += adjusted
        }
        else {
          misses += path.toUri().toString()
        }
      }
      results to misses
    }

    if (misses.isNotEmpty()) {
      val urls = misses.asSequence().map { "&nbsp;&nbsp;&nbsp;${it}" }.joinToString("<br>")
      val message = UIBundle.message("file.chooser.vfs.lookup", urls)
      Messages.showErrorDialog(parent, message, title)
      return emptyArray()
    }

    if (results.isEmpty()) return emptyArray()

    try {
      val selectedFiles = VfsUtilCore.toVirtualFileArray(results)
      descriptor.validateSelectedFiles(selectedFiles)
      return selectedFiles
    }
    catch (e: Exception) {
      Messages.showErrorDialog(parent, e.message, title)
      return emptyArray()
    }
  }

  private fun toVirtualFile(path: Path): VirtualFile? =
    if (path.fileSystem == FileSystems.getDefault()) {
      localFs.value.refreshAndFindFileByPath(path.toString())
    }
    else try {
      val store = path.fileSystem.fileStores.iterator().next()
      if (store.type() == ZIP_FS_TYPE) {
        val localPath = UriUtil.trimTrailingSlashes(store.name())
        jarFs.value.refreshAndFindFileByPath(UriUtil.trimTrailingSlashes(localPath) + '!' + path)
      }
      else null
    }
    catch (e: Exception) {
      Logger.getInstance(FileChooserDialogHelper::class.java).warn(e)
      null
    }

  override fun accept(dir: File, name: String): Boolean =
    try {
      descriptor.isFileSelectable(localFs.value.refreshAndFindFileByPath(dir.absolutePath + File.separatorChar + name))
    }
    catch (t: Throwable) {
      Logger.getInstance(FileChooserDialogHelper::class.java).warn(t)
      false
    }
}
