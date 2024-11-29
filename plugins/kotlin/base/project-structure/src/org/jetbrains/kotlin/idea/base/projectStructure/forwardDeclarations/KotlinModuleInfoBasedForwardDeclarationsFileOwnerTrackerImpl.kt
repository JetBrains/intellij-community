// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryInfoListener
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKaModule
import java.util.concurrent.ConcurrentHashMap

/**
 * IMPORTANT: ModuleInfo-based implementation should be replaced/removed together with
 * [org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations.KotlinForwardDeclarationsModuleInfoProviderExtension]
 *
 * The implementation via service is chosen over a more straightforward approach via file user data for a reason.
 * There's no entity with a suitable lifetime to hold such info reliably.
 * [VirtualFile]s are application level while the module info objects are project level — [VirtualFile] user data doesn't fit.
 * Project-level [com.intellij.psi.PsiFile]s are more suitable, but they can be recreated while the containing library is still alive.
 * In such cases the user data will be lost, which will lead to errors.
 * To avoid this, the cache explicitly tracks the mapping, taking the relevant library changes into account.
 */
private class KotlinModuleInfoBasedForwardDeclarationsFileOwnerTrackerImpl(
    project: Project,
) : KotlinForwardDeclarationsFileOwnerTracker, LibraryInfoListener, Disposable {
    private val cache: MutableMap<VirtualFile, IdeaModuleInfo> = ConcurrentHashMap()

    init {
        project.messageBus.connect(this).subscribe(LibraryInfoListener.TOPIC, this)
    }

    override fun getFileOwner(virtualFile: VirtualFile): KaModule? {
        return cache[virtualFile]?.toKaModule()
    }

    override fun registerFileOwner(virtualFile: VirtualFile, owner: KaModule) {
        cache.put(virtualFile, owner.moduleInfo)
    }

    override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
        cache.values.removeAll(libraryInfos)
    }

    override fun dispose() {}
}
