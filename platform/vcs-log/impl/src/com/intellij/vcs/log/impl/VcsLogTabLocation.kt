// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.project.Project
import com.intellij.vcs.log.VcsLogUi

enum class VcsLogTabLocation {
  TOOL_WINDOW {
    override fun select(project: Project, logUi: VcsLogUi): Boolean = VcsLogContentUtil.selectLogUi(project, logUi)
  },
  EDITOR {
    override fun select(project: Project, logUi: VcsLogUi): Boolean = VcsLogEditorUtil.selectLogUi(project, logUi)
  },
  STANDALONE {
    override fun select(project: Project, logUi: VcsLogUi): Boolean {
      throw UnsupportedOperationException("Selecting standalone log tabs is not supported")
    }
  };

  abstract fun select(project: Project, logUi: VcsLogUi): Boolean

  companion object {
    fun <U : VcsLogUi> VcsLogManager.findLogUi(location: VcsLogTabLocation, clazz: Class<U>, select: Boolean, condition: (U) -> Boolean): U? {
      val logUi = getLogUis(location).filterIsInstance(clazz).find(condition)
      if (select && logUi != null) {
        if (!location.select(dataManager.project, logUi)) return null
      }
      return logUi
    }
  }
}