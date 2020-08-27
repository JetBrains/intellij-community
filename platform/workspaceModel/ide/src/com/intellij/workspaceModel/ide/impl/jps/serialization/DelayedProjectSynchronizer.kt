// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.configLocation
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl

class DelayedProjectSynchronizer : StartupActivity {
  override fun runActivity(project: Project) {
    if ((WorkspaceModel.getInstance(project) as WorkspaceModelImpl).loadedFromCache) {
      // invokeLater / write locks / AWT  ?
      JpsProjectModelSynchronizer.getInstance(project)?.loadRealProject(project.configLocation!!)
    }
  }
}