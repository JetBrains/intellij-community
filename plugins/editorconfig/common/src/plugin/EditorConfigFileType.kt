package com.intellij.editorconfig.common.plugin

import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.editorconfig.common.syntax.EditorConfigLanguage
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls
import javax.swing.Icon

object EditorConfigFileType : LanguageFileType(EditorConfigLanguage) {
  override fun getName(): String = fileTypeName
  override fun getDescription(): @Nls String = EditorConfigBundle.get("file.type.description")
  override fun getDefaultExtension(): String = fileExtension
  override fun getIcon(): Icon = AllIcons.Nodes.Editorconfig
  override fun getCharset(file: VirtualFile, content: ByteArray): String = CharsetToolkit.UTF8

  val fileTypeName: String get() = "EditorConfig"
  val fileExtension: String get() = "editorconfig"
}