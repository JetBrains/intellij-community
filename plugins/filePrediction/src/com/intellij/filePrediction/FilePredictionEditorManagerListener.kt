// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.util.registry.Registry

class FilePredictionEditorManagerListener : FileEditorManagerListener {
  override fun selectionChanged(event: FileEditorManagerEvent) {
    if (Registry.get("filePrediction.calculate.features").asBoolean() && ApplicationManager.getApplication().isEAP) {
      event.newFile?.let { FileUsagePredictor.onFileOpened(event.manager.project, it, event.oldFile) }
    }
  }
}