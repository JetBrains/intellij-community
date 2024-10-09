// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.content.Content
import javax.swing.Icon


class ToolWindowTabFileImpl(fileName: String, val content: Content, icon: Icon?) : LightVirtualFile(fileName, ToolWindowTabSessionFileType(icon), "") {

  override fun setWritable(writable: Boolean) {
    super.isWritable = true
  }

  private class ToolWindowTabSessionFileType(val myIcon: Icon?) : FakeFileType() {

    override fun getName() = "Tool Window Tab name"

    @NlsSafe
    override fun getDescription() = "$name Fake File Type"

    override fun getIcon() = myIcon

    override fun isMyFileType(file: VirtualFile) = file is ToolWindowTabFileImpl

  }
}