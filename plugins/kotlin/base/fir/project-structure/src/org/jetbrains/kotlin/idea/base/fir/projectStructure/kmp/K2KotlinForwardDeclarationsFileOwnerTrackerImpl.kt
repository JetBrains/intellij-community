// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.kmp

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.KotlinForwardDeclarationsFileOwnerTracker
import org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.KotlinModuleInfoBasedForwardDeclarationsFileOwnerTrackerImpl
import org.jetbrains.kotlin.idea.base.projectStructure.useNewK2ProjectStructureProvider

// does nothing if `useNewK2ProjectStructureProvider` enabled
internal class K2KotlinForwardDeclarationsFileOwnerTrackerImpl(project: Project) : KotlinForwardDeclarationsFileOwnerTracker {
    private val delegate: KotlinForwardDeclarationsFileOwnerTracker? = if (useNewK2ProjectStructureProvider) {
        null
    } else {
        KotlinModuleInfoBasedForwardDeclarationsFileOwnerTrackerImpl(project)
    }

    override fun getFileOwner(virtualFile: VirtualFile): KaModule? {
        return delegate?.getFileOwner(virtualFile)
    }

    override fun registerFileOwner(virtualFile: VirtualFile, owner: KaModule) {
        delegate?.registerFileOwner(virtualFile, owner)
    }
}