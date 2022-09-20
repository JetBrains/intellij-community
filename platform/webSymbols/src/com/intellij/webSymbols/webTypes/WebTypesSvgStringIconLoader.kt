package com.intellij.webSymbols.webTypes

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.IconLoader
import com.intellij.util.io.DigestUtil
import java.io.File
import java.security.MessageDigest
import javax.swing.Icon

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