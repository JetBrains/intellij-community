// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.ImageUtil
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

internal object ProjectSelfieUtil {
  val isEnabled: Boolean
    get() = Registry.`is`("ide.project.loading.show.last.state", true)

  fun getSelfieLocation(projectWorkspaceId: String): Path {
    return appSystemDir.resolve("project-selfies").resolve("$projectWorkspaceId.png")
  }

  @Throws(IOException::class)
  fun readProjectSelfie(value: String, scaleContext: ScaleContext): Image? {
    val location = getSelfieLocation(value)
    var bufferedImage: BufferedImage
    try {
      Files.newInputStream(location).use { input ->
        val reader = ImageIO.getImageReadersByFormatName("png").next()
        try {
          MemoryCacheImageInputStream(input).use { stream ->
            reader.setInput(stream, true, true)
            bufferedImage = reader.read(0, null)
          }
        }
        finally {
          reader.dispose()
        }
      }
    }
    catch (ignore: NoSuchFileException) {
      return null
    }
    return ImageUtil.ensureHiDPI(bufferedImage, scaleContext)
  }
}