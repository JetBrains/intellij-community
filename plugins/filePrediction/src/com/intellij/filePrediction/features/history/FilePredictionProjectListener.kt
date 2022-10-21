// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManagerListener

class FilePredictionProjectListener : ProjectCloseListener {
  override fun projectClosing(project: Project) {
    FilePredictionHistory.getInstanceIfCreated(project)?.saveFilePredictionHistory(project)
  }
}