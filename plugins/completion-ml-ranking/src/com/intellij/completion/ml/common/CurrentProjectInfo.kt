// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.common

import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import java.nio.file.Paths

class CurrentProjectInfo(project: Project) {
  val isIdeaProject = project.basePath?.let {
    Paths.get(it, "intellij.idea.ultimate.main.iml").exists() || Paths.get(it, "intellij.idea.community.main.iml").exists()
  } ?: false

  companion object {
    fun getInstance(project: Project): CurrentProjectInfo = project.getService(CurrentProjectInfo::class.java)
  }
}