// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.impl.FileChooserUtil
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.EelProviderUtil
import com.intellij.util.PlatformIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributes
import javax.swing.Icon
import kotlin.io.path.name
import kotlin.streams.asSequence

internal object NioFileChooserUtil {

  fun isHidden(path: Path): Boolean {
    if (EelProviderUtil.getOsFamily(path) == EelOsFamily.Windows) {
      val dosAttrs = runCatching {
        Files.readAttributes(path, DosFileAttributes::class.java)
      }.getOrElse { null }
      return isHidden(path, dosAttrs)
    }
    else {
      return isHidden(path, null)
    }
  }

  fun isHidden(path: Path, attrs: BasicFileAttributes?): Boolean {
    if (EelProviderUtil.getOsFamily(path) == EelOsFamily.Windows && attrs is DosFileAttributes) {
      return attrs.isHidden
    }
    else {
      val fileName = path.fileName
      return fileName != null && fileName.toString().startsWith(".")
    }
  }

  fun toNioPathSafe(file: VirtualFile): Path? {
    return try {
      file.toNioPath()
    }
    catch (_: UnsupportedOperationException) {
      null
    }
  }

  /**
   * Returns the roots of the [descriptor] as NIO paths, without duplicates.
   * The result joins [FileChooserDescriptor.getPathRoots] with the
   * [FileChooserDescriptor.getRoots] entries that map to a path.
   */
  fun getNioRoots(descriptor: FileChooserDescriptor): List<Path> {
    val fromVirtualFiles = descriptor.roots.mapNotNull { toNioPathSafe(it) }
    return (descriptor.pathRoots + fromVirtualFiles).distinct()
  }

  /** A child of a listed directory. The [name] is the file name of the child. */
  internal data class DirectoryEntry(
    val name: String,
    val isDirectory: Boolean,
    val icon: Icon?,
  )

  /**
   * Returns the sorted children of the [directory] with their icons, or an empty list when the directory cannot be listed.
   * The listing runs on [Dispatchers.IO]. It checks the cancellation before each child, so a cancelled caller stops early.
   */
  suspend fun safeGetChildren(directory: Path, showHidden: Boolean, showFiles: Boolean, showArchives: Boolean = false): List<DirectoryEntry> =
    withContext(Dispatchers.IO) {
      val job = coroutineContext.job
      try {
        Files.list(directory).use { stream ->
          stream.asSequence()
            .mapNotNull { path ->
              job.ensureActive()
              val isDirectory = Files.isDirectory(path)
              val shown = runCatching {
                (showFiles || isDirectory || (showArchives && isArchiveFile(path))) && (showHidden || !isHidden(path))
              }.getOrElse { false }
              if (shown) DirectoryEntry(path.name, isDirectory, getIcon(path.name, isDirectory)) else null
            }
            .sortedBy { it.name.lowercase() }.toList()
        }
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        emptyList()
      }
    }

  fun isArchiveFile(path: Path): Boolean {
    val name = path.fileName?.toString() ?: return false
    return FileTypeRegistry.getInstance().getFileTypeByFileName(name) === ArchiveFileType.INSTANCE
  }

  private fun getIcon(name: String, isDirectory: Boolean): Icon? =
    if (isDirectory) PlatformIcons.FOLDER_ICON
    else FileTypeRegistry.getInstance().getFileTypeByFileName(name).icon

  @ApiStatus.Internal
  fun getLastOpenedPath(project: Project?): Path? {
    val last = FileChooserUtil.getLastOpenedFilePath(project)
    return if (last != null) NioFiles.toPath(FileUtil.toSystemDependentName(last)) else null
  }
}

@Service(Service.Level.APP)
internal class FileWatcherAppScopeHolder(val scope: CoroutineScope) {
  companion object {
    fun getInstance(): FileWatcherAppScopeHolder = service<FileWatcherAppScopeHolder>()
  }
}