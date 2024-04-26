// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker

@Service(Service.Level.PROJECT)
class ScriptDependenciesModificationTracker : SimpleModificationTracker() {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): ScriptDependenciesModificationTracker = project.service()
    }
}