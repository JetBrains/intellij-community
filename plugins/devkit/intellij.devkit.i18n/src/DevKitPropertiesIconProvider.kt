// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.i18n

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.devkit.DevKitIcons
import javax.swing.Icon

internal class DevKitPropertiesIconProvider : FileIconProvider {
  private val pattern: Regex = Regex("(.*)Bundle(_.+)?\\.properties")

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    if (file.parent?.name == "messages"
        && file.name.matches(pattern)) {
      return DevKitIcons.LocalizationFile
    }
    return null
  }
}