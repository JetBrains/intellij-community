// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.kotlin.idea.core.script.k1.configuration.utils.ScriptClassRootsStorage

class ScriptCacheDependenciesFileInvalidator : CachesInvalidator() {
    override fun invalidateCaches() {
        ProjectManager.getInstance().openProjects.forEach {
            ScriptClassRootsStorage.getInstance(it).clear()
        }
    }
}