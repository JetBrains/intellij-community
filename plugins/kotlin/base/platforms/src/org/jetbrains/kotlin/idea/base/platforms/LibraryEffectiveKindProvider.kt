// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.ProjectTopics
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.util.containers.SoftFactoryMap

@Service(Service.Level.PROJECT)
class LibraryEffectiveKindProvider(project: Project) {
    private val effectiveKindMap = object : SoftFactoryMap<LibraryEx, PersistentLibraryKind<*>?>() {
        override fun create(key: LibraryEx) = detectLibraryKind(key.getFiles(OrderRootType.CLASSES))
    }

    init {
        project.messageBus.connect().subscribe(
            ProjectTopics.PROJECT_ROOTS,
            object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) {
                    effectiveKindMap.clear()
                }
            }
        )
    }

    fun getEffectiveKind(library: LibraryEx): PersistentLibraryKind<*>? {
        if (library.isDisposed) {
            return null
        }

        return when (val kind = library.kind) {
            is KotlinLibraryKind -> kind
            else -> effectiveKindMap.get(library)
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): LibraryEffectiveKindProvider = project.service()
    }
}