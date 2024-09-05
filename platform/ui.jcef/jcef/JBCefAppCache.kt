// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef

import com.intellij.execution.Platform
import com.intellij.ide.IdeBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.NotNull
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

private const val invalidationMarkerFileName = "invalidation.marker"

@Service(Service.Level.APP)
internal class JBCefAppCache {
  @get:NotNull
  val path: Path by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    prepareCachePath()
  }

  fun markInvalidated() {
    FileUtil.createIfDoesntExist(path.resolve(invalidationMarkerFileName).toFile())
  }

  private fun prepareCachePath(): Path {
    val fileSeparator = Platform.current().fileSeparator
    val defaultCachePath = "${PathManager.getSystemPath()}${fileSeparator}jcef_cache${fileSeparator}"
    val suggestedPath: Path = Paths.get(System.getProperty("ide.browser.jcef.cache.path", defaultCachePath))

    val invalidationMarkerFilePath = suggestedPath.resolve(invalidationMarkerFileName)
    val logger = thisLogger()

    if (FileUtil.exists(invalidationMarkerFilePath.toString())) {
      try {
        FileUtil.delete(suggestedPath)
        logger.info("Successfully deleted JCEF browser engine cache at \"$suggestedPath\"")
      }
      catch (exception: IOException) {
        Notifications.Bus.notify(
          Notification(
            "IDE Caches",
            IdeBundle.message("jcef.local.cache.invalidate.failed.title"),
            IdeBundle.message("jcef.local.cache.invalidate.failed.message", exception.message),
            NotificationType.ERROR
          )
        )

        logger.error("Failed to cleanup JCEF browser engine cache due to I/O error", exception)
      }
    }

    logger.debug("JCEF cache path: \"$suggestedPath\"")
    return suggestedPath
  }
}
