package com.intellij.javascript.web.webTypes

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import javax.swing.Icon

class WebTypesFileSystemIconLoader(private val context: List<VirtualFile>) {
  fun loadIcon(path: String): Icon? =
    context.asSequence()
      .mapNotNull { root -> root.parent?.takeIf { it.isValid }?.findFileByRelativePath(path) }
      .firstOrNull()
      ?.let { File(it.path) }
      ?.takeIf { it.exists() }
      ?.let { file ->
        IconLoader.findIcon(file.toURI().toURL())
          ?.takeIf { it.iconHeight > 1 && it.iconWidth > 1 }
      }

}