// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.filetype

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import org.editorconfig.language.EditorConfigLanguage
import org.editorconfig.language.messages.EditorConfigBundle
import javax.swing.Icon

object EditorConfigFileType : LanguageFileType(EditorConfigLanguage) {
  override fun getName() = EditorConfigFileConstants.FILETYPE_NAME
  override fun getDescription() = EditorConfigBundle.get("file.type.description")
  override fun getDefaultExtension() = EditorConfigFileConstants.FILE_EXTENSION
  override fun getIcon(): Icon = AllIcons.Nodes.Editorconfig
  override fun getCharset(file: VirtualFile, content: ByteArray): String = CharsetToolkit.UTF8
}
