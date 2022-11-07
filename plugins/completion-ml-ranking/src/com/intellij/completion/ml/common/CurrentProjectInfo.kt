// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.common

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.libraries.LibraryUtil
import com.intellij.util.Alarm
import com.intellij.util.Time
import com.intellij.util.io.exists
import java.nio.file.Paths

class CurrentProjectInfo(project: Project) : Disposable {
  private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  private val updateInterval = Time.DAY
  private var _modulesCount: Int = 0
  private var _librariesCount: Int = 0
  private var _filesCount: Int = 0

  init {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      alarm.addRequest({ updateStats(project) }, 10)
    }
  }

  val isIdeaProject = project.basePath?.let {
    Paths.get(it, "intellij.idea.ultimate.main.iml").exists() || Paths.get(it, "intellij.idea.community.main.iml").exists()
  } ?: false

  val modulesCount: Int get() = _modulesCount

  val librariesCount: Int get() = _librariesCount

  val filesCount: Int get() = _filesCount

  override fun dispose() = Unit

  private fun countFiles(project: Project): Int {
    var counter = 0
    val projectFileIndex = ProjectFileIndex.getInstance(project)
    projectFileIndex.iterateContent {
      if (!it.isDirectory && runReadAction { projectFileIndex.isInSourceContent(it) }) {
        counter++
      }
      return@iterateContent true
    }
    return counter
  }

  private fun updateStats(project: Project) {
    try {
      _modulesCount = ModuleManager.getInstance(project).modules.size
      _librariesCount = LibraryUtil.getLibraryRoots(project).size
      _filesCount = countFiles(project)
    } finally {
      alarm.addRequest({ updateStats(project) }, updateInterval)
    }
  }

  companion object {
    fun getInstance(project: Project): CurrentProjectInfo = project.getService(CurrentProjectInfo::class.java)
  }
}
