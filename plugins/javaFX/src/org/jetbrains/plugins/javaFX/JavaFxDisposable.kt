// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project

@Service
class JavaFxDisposable(val project: Project) : Disposable {
  override fun dispose() {
  }

  companion object {
    @JvmStatic fun getInstance(project: Project): JavaFxDisposable {
      return ServiceManager.getService(project, JavaFxDisposable::class.java)
    }
  }
}
