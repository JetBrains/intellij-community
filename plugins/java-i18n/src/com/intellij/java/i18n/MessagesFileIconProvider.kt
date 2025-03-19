// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.i18n

import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

internal class MessagesFileIconProvider : FileIconProvider {
  private val messagesPattern: Regex = Regex("messages(_[a-z]+)?\\.properties")
  private val validationMessagesPattern: Regex = Regex("(.*)Messages(_[a-z]+)?\\.properties")
  private val bundlePattern: Regex = Regex("(.*)Bundle(_.+)?\\.properties")

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    if (file.name.matches(messagesPattern)
        || validationMessagesPattern.matches(file.name)) {
      return AllIcons.FileTypes.I18n
    }
    if (file.parent?.name == "messages"
        && file.name.matches(bundlePattern)) {
      return AllIcons.FileTypes.I18n
    }
    return null
  }
}