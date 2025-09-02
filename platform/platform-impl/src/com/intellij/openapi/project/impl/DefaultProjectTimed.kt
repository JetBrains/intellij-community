// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.ModalityUiUtil
import com.intellij.util.TimedReference
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class DefaultProjectTimed internal constructor(
  private val parentDisposable: DefaultProject,
) : TimedReference<Project>(parentDisposable) {
  abstract fun compute(): Project

  abstract fun init(project: Project)

  @Synchronized
  fun markRequested() {
    if (myT != null) {
      super.get()
    }
  }

  @Synchronized
  override fun get(): Project {
    super.get()?.let {
      return it
    }

    val value = compute()
    set(value)
    init(value)
    // disable "the only project" optimization since we have now more than one project.
    // (even though the default project is not a real project, it can be used indirectly in e.g. "Settings|Code Style" code fragments PSI)
    (ProjectManager.getInstance() as ProjectManagerImpl).updateTheOnlyProjectField()
    return value
  }

  override fun dispose() {
    // project must be disposed in EDT in write action
    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.nonModal(), parentDisposable.getDisposed()) {
      if (isCached) {
        ApplicationManager.getApplication().runWriteAction {
          super.dispose()
        }
      }
    }
  }
}
