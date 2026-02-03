// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

open class JComponentFileType(@JvmField @NonNls protected val name: String = "JComponent",
                              @JvmField @NlsContexts.Label protected val description: String = "",
                              @JvmField protected val icon: Icon = AllIcons.FileTypes.Text) : FakeFileType() {
  companion object {
    val INSTANCE: JComponentFileType = JComponentFileType()
  }

  override fun getName(): String = name

  override fun getDescription(): String = description

  override fun isMyFileType(file: VirtualFile): Boolean = JComponentEditorProviderUtils.isJComponentFile(file)

  override fun getIcon(): Icon = icon
}