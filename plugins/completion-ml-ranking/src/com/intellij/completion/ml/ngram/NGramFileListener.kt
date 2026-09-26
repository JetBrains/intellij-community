// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.ngram

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

private class NGramFileListener(private val project: Project) : FileEditorManagerListener.Before {
  override fun beforeFileOpened(source: FileEditorManager, file: VirtualFile) {
    if (LightEdit.owns(project)) {
      return
    }

    val language = (file.fileType as? LanguageFileType)?.language ?: return
    if (!NGram.isSupported(language)) {
      return
    }

    NGramModelRunnerManager.getInstance(project).scheduleAnalysis(file)
  }
}