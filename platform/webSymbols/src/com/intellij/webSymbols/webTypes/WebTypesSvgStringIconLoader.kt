package com.intellij.webSymbols.webTypes

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.IconLoader
import com.intellij.util.io.DigestUtil
import org.jetbrains.annotations.ApiStatus
import java.io.File
import javax.swing.Icon

@ApiStatus.Internal
object WebTypesSvgStringIconLoader {

  fun loadIcon(svg: String): Icon? =
    File(PathManager.getPluginTempPath(), "web-types-svg-cache/${DigestUtil.sha256Hex(svg.toByteArray())}.svg")
      .also {
        if (!it.exists()) {
          it.parentFile.mkdirs()
          it.writeText(svg)
        }
      }
      .let { file ->
        IconLoader.findIcon(file.toURI().toURL())
          ?.takeIf { it.iconHeight > 1 && it.iconWidth > 1 }
      }
}