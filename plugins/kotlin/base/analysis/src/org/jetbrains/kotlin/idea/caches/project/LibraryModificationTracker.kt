// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.project

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker

@Service(Service.Level.PROJECT)
@Deprecated("use JavaLibraryModificationTracker instead")
class LibraryModificationTracker(project: Project) : ModificationTracker {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): LibraryModificationTracker = project.service()
    }

    private val delegate = JavaLibraryModificationTracker.getInstance(project)

    override fun getModificationCount(): Long = delegate.modificationCount

}

