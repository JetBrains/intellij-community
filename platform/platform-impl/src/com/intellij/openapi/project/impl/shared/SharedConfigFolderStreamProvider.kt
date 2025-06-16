// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl.shared

import com.intellij.configurationStore.NOTIFICATION_GROUP_ID
import com.intellij.configurationStore.StorageManagerFileWriteRequestor
import com.intellij.configurationStore.StreamProvider
import com.intellij.configurationStore.getFileRelativeToRootConfig
import com.intellij.diagnostic.DiagnosticBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.util.io.basicAttributesIfExists
import org.jdom.JDOMException
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.xml.stream.XMLStreamException
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.name

/**
 * @param root $ROOT_CONFIG$ to watch (aka <config>, idea.config.path)
 */
internal class SharedConfigFolderStreamProvider(private val root: Path) : StreamProvider, StorageManagerFileWriteRequestor {
  internal val configFilesUpdatedByThisProcess = ConfigFilesUpdatedByThisProcess()

  override val isExclusive: Boolean = true

  override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
    if (!isApplicable(fileSpec, roamingType)) {
      return false
    }

    val file = resolveSpec(fileSpec)
    if (checkFile(file)) {
      try {
        SharedConfigFolderUtil.readNonEmptyFileWithRetries(file) { stream ->
          LOG.trace { "read ${file.fileSize()} bytes from $file" }
          stream.use(consumer)
        }
      }
      catch (e: JDOMException) {
        processReadException(e, file, consumer)
      }
      catch (e: XMLStreamException) {
        processReadException(e, file, consumer)
      }
      catch (e: IOException) {
        processReadException(e, file, consumer)
      }
    }
    else {
      consumer(null)
    }
    return true
  }

  private fun processReadException(e: Exception, file: Path, consumer: (InputStream?) -> Unit) {
    LOG.warn(e)
    val app = ApplicationManager.getApplication()
    if (app != null && !app.isUnitTestMode && !app.isHeadlessEnvironment) {
      Notification(NOTIFICATION_GROUP_ID,
                   DiagnosticBundle.message("notification.cannot.load.settings.title"),
                   DiagnosticBundle.message("notification.cannot.load.settings.content", file, e.message),
                   NotificationType.WARNING)
        .notify(null)
    }
    consumer(null)
  }

  override fun processChildren(path: String,
                               roamingType: RoamingType,
                               filter: (name: String) -> Boolean,
                               processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean): Boolean {
    val file = root.resolve(path)
    if (!file.exists()) return true

    Files.walkFileTree(file, object : SimpleFileVisitor<Path>() {
      override fun visitFile(child: Path, attrs: BasicFileAttributes): FileVisitResult {
        if (!filter(child.name)) return FileVisitResult.CONTINUE
        if (!checkFile(child)) return FileVisitResult.CONTINUE

        val abort = SharedConfigFolderUtil.readNonEmptyFileWithRetries(child) { stream ->
          stream.use { !processor(child.name, it, false) }
        }
        if (abort) FileVisitResult.TERMINATE
        return FileVisitResult.CONTINUE
      }
    })
    return true
  }

  override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
    if (fileSpec == StoragePathMacros.CACHE_FILE) {
      return
    }
    
    val file = resolveSpec(fileSpec)
    LOG.trace { "write ${content.size} bytes to $file" }
    configFilesUpdatedByThisProcess.saved(fileSpec, content)
    SharedConfigFolderUtil.writeToSharedFile(file, content)
  }

  override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
    if (!isApplicable(fileSpec, roamingType)) {
      return false
    }

    val file = resolveSpec(fileSpec)
    LOG.trace { "delete $file" }
    configFilesUpdatedByThisProcess.deleted(fileSpec)
    SharedConfigFolderUtil.deleteSharedFile(file)
    return true
  }

  private fun resolveSpec(fileSpec: String): Path = root.resolve(getFileRelativeToRootConfig(fileSpec))

  private fun checkFile(file: Path): Boolean {
    val attributes = file.basicAttributesIfExists()
    return attributes != null && !attributes.isDirectory
  }
}

private val LOG = logger<SharedConfigFolderStreamProvider>()
