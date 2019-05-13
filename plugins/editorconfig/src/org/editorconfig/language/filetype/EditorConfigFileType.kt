// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.filetype

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import org.editorconfig.language.EditorConfigLanguage
import org.editorconfig.language.messages.EditorConfigBundle
import javax.swing.Icon

object EditorConfigFileType : LanguageFileType(EditorConfigLanguage) {
  override fun getName() = EditorConfigFileConstants.FILETYPE_NAME
  override fun getDescription() = EditorConfigBundle["file.type.description"]
  override fun getDefaultExtension() = EditorConfigFileConstants.FILE_EXTENSION
  override fun getIcon(): Icon? = AllIcons.Nodes.Editorconfig
}
