// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.encoding.EncodingManager
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.util.ThrowableRunnable

@JvmOverloads
fun doEncodingTest(project: Project, newIdeCharset: String? = "UTF-8", newProjectCharset: String? = CharsetToolkit.WIN_1251_CHARSET.name(), task: ThrowableRunnable<Exception>) {
  val encodingManager = EncodingManager.getInstance()
  val oldIde = encodingManager.defaultCharsetName
  if (newIdeCharset != null) {
    encodingManager.defaultCharsetName = newIdeCharset
  }

  val encodingProjectManager = EncodingProjectManager.getInstance(project)
  val oldProject = encodingProjectManager.defaultCharsetName
  if (newProjectCharset != null) {
    encodingProjectManager.defaultCharsetName = newProjectCharset
  }

  try {
    task.run()
  }
  finally {
    if (newIdeCharset != null) {
      encodingManager.defaultCharsetName = oldIde
    }
    if (newProjectCharset != null) {
      encodingProjectManager.defaultCharsetName = oldProject
    }
  }
}