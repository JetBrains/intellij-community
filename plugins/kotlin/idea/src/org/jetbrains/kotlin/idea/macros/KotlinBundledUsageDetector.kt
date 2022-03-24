// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.macros

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.startup.StartupActivity
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.idea.versions.forEachAllUsedLibraries
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext

@Service
class KotlinBundledUsageDetector : Disposable {
    /**
     * When this variable is `false` then it means that KOTLIN_BUNDLED certainly NOT used in any JPS library.
     * If this variable is `true` then it means that KOTLIN_BUNDLED is potentially used in the project/module libraries
     */
    val coroutineScope = CoroutineScope(EmptyCoroutineContext)
    private val _isKotlinBundledPotentiallyUsedInLibraries = MutableStateFlow(false)
    val isKotlinBundledPotentiallyUsedInLibraries: StateFlow<Boolean>
        get() = _isKotlinBundledPotentiallyUsedInLibraries

    class MyStartupActivity : StartupActivity.DumbAware {
        override fun runActivity(project: Project) {
            WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(project.messageBus.connect(), object :
                WorkspaceModelChangeListener {
                override fun changed(event: VersionedStorageChange) {
                    val detector = getInstance(project)
                    if (detector.isKotlinBundledPotentiallyUsedInLibraries.value) {
                        return
                    }
                    val isDistUsedInLibraries = event.getChanges(LibraryEntity::class.java).asSequence()
                        .mapNotNull {
                            when (it) {
                                is EntityChange.Added -> it.entity
                                is EntityChange.Removed -> null
                                is EntityChange.Replaced -> it.newEntity
                            }
                        }
                        .flatMap { it.roots }
                        .map { File(JpsPathUtil.urlToPath(it.url.url)) }
                        .any { it.startsWith(KotlinArtifacts.KOTLIN_DIST_LOCATION_PREFIX) }
                    if (isDistUsedInLibraries) {
                        detector._isKotlinBundledPotentiallyUsedInLibraries.value = true
                    }
                }
            })

            var isUsed = false
            project.forEachAllUsedLibraries { library ->
                if (
                    library.getUrls(OrderRootType.CLASSES)
                        .map { File(JpsPathUtil.urlToPath(it)) }
                        .any { it.startsWith(KotlinArtifacts.KOTLIN_DIST_LOCATION_PREFIX) }
                ) {
                    isUsed = true
                    return@forEachAllUsedLibraries false
                }
                return@forEachAllUsedLibraries true
            }

            if (isUsed) {
                getInstance(project)._isKotlinBundledPotentiallyUsedInLibraries.value = true
            }
        }
    }

    override fun dispose() {
        coroutineScope.cancel()
    }

    companion object {
        fun getInstance(project: Project): KotlinBundledUsageDetector = project.getServiceSafe()
    }
}
