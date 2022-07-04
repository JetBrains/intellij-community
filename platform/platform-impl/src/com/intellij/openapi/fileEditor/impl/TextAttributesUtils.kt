// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

fun getTextAttributesForFile(project: Project, file: VirtualFile): TextAttributes? {
  var resultAttributes: TextAttributes? = TextAttributes()

  for (provider in EditorTabColorProvider.EP_NAME.extensionList) {
    val attributes = provider.getEditorTabTextAttributes(project, file)
    if (attributes != null) {
      resultAttributes = TextAttributes.merge(resultAttributes, attributes)
    }
  }
  return resultAttributes
}

