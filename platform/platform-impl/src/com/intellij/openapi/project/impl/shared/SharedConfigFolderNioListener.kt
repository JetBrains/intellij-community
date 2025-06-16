// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl.shared

import com.intellij.diagnostic.LoadingState
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.createDirectories
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CompletableFuture
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * @param root $ROOT_CONFIG$ to watch (aka <config>, idea.config.path)
 */
internal class SharedConfigFolderNioListener(private val root: Path, private val configFilesUpdatedByThisProcess: ConfigFilesUpdatedByThisProcess) {
  private val watcher = FileSystems.getDefault().newWatchService()
  private val keys = mutableMapOf<WatchKey, Path>()

  fun init() {
    root.createDirectories()
    watchPath(root)
    root.listDirectoryEntries().forEach { subRoot ->
      if (subRoot.isDirectory() && subRoot.fileName.toString() in namesOfConfigSubDirectories) {
        walkDirectory(subRoot) { path, isDirectory ->
          if (isDirectory) watchPath(path)
        }
      }
    }

    CompletableFuture.runAsync(
      {
        val modified = mutableSetOf<String>()
        val deleted = mutableSetOf<String>()
        while (true) {
          try {
            val watchKey = watcher.take()
            val path = keys[watchKey] ?: continue

            val events = watchKey.pollEvents()
            val valid = watchKey.reset()
            if (!valid) {
              keys.remove(watchKey)
            }

            processEvents(path, events, modified, deleted)
          }
          catch (ignored: InterruptedException) {
          }
          catch (e: ClosedWatchServiceException) {
            break
          }
        }
      }, ProcessIOExecutorService.INSTANCE)
      .exceptionally { t: Throwable ->
        logger<SharedConfigFolderNioListener>().error(t)
        null
      }
  }

  private fun processEvents(path: Path, events: MutableList<WatchEvent<*>>, modified: MutableSet<String>, deleted: MutableSet<String>) {
    for (event in events) {
      val kind = event.kind()
      val fileName = event.context() as? Path ?: continue

      LOG.trace { "event $kind received for '$fileName'" }
      val eventPath = path.resolve(fileName)

      val fileSpec = getSpecFor(eventPath)
      if (fileSpec != null) {
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
          if (!configFilesUpdatedByThisProcess.wasDeleted(fileSpec)) {
            deleted.add(fileSpec)
          }
          else {
            LOG.trace { "skipped deleted file $fileName because it was deleted by this process" }
          }
        }
        else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
          if (!configFilesUpdatedByThisProcess.wasWritten(fileSpec, eventPath)) {
            modified.add(fileSpec)
          }
          else {
            LOG.trace { "skipped modified file $fileName because it was written by this process" }
          }
        }
      }

      if (kind === StandardWatchEventKinds.ENTRY_CREATE) {
        try {
          if (Files.isDirectory(eventPath, LinkOption.NOFOLLOW_LINKS)) {
            var ancestorUnderRoot: Path? = eventPath
            while (ancestorUnderRoot != null && ancestorUnderRoot.parent != root) {
              ancestorUnderRoot = ancestorUnderRoot.parent
            }
            if (ancestorUnderRoot != null && ancestorUnderRoot.fileName.toString() in namesOfConfigSubDirectories) {
              walkDirectory(eventPath) { path, isDirectory ->
                if (isDirectory) {
                  watchPath(path)
                }
                else {
                  ContainerUtil.addIfNotNull(modified, getSpecFor(path))
                }
              }
            }
            else {
              LOG.trace { "skipped created directory $eventPath because it's not under known config subdirectories"}
            }
          }
        }
        catch (ignore: IOException) {
        }
      }
    }

    if ((modified.isNotEmpty() || deleted.isNotEmpty()) && LoadingState.COMPONENTS_LOADED.isOccurred) {
      val modifiedToReload = LinkedHashSet(modified)
      val deletedToReload = LinkedHashSet(deleted)
      modified.clear()
      deleted.clear()
      ApplicationManager.getApplication()?.invokeLater {
        SharedConfigFolderUtil.reloadComponents(modifiedToReload, deletedToReload)
      }
    }
  }


  private fun watchPath(path: Path) {
    try {
      val watchKey = path.register(watcher,
                                     arrayOf(StandardWatchEventKinds.ENTRY_CREATE,
                                           StandardWatchEventKinds.ENTRY_DELETE,
                                           StandardWatchEventKinds.ENTRY_MODIFY))
      keys[watchKey] = path
      LOG.debug { "Watch path $path" }
    }
    catch (e: Throwable) {
      LOG.error("Failed to watch for changes in $path", e)
    }
  }

  private fun walkDirectory(dir: Path, task: (Path, isDirectory: Boolean) -> Unit) {
    Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
      override fun preVisitDirectory(path: Path, attrs: BasicFileAttributes): FileVisitResult {
        task(path, true)
        return FileVisitResult.CONTINUE
      }

      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        task(file, false)
        return FileVisitResult.CONTINUE
      }
    })
  }

  private fun getSpecFor(path: Path): String? {
    if (path.startsWith(root)) {
      return root.relativize(path).invariantSeparatorsPathString.removePrefix("${PathManager.OPTIONS_DIRECTORY}/")
    }
    return null
  }
}

private val LOG = logger<SharedConfigFolderNioListener>()

/**
 * Names of subdirectories of the root config directory which contain files serialized via configurationStore. 
 * `options` is used by regular [com.intellij.openapi.components.PersistentStateComponent], others correspond to 
 * [SchemaManager][com.intellij.openapi.options.SchemeManagerFactory.create]'s instances.
 * TODO: support directories for schemes from external plugins.
 */
private val namesOfConfigSubDirectories = setOf(
  "codestyles",
  "colors",
  "filetypes",
  "inspection",
  "keymaps",
  "options",
  "quicklists",
  "remoteTools",
  "templates",
  "tools",
)