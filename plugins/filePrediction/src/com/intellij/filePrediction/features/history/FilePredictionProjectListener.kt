// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.filePrediction.features.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener

private class FilePredictionProjectListener : ProjectCloseListener {
  override fun projectClosing(project: Project) {
    FilePredictionHistory.getInstanceIfCreated(project)?.saveFilePredictionHistory(project)
  }
}