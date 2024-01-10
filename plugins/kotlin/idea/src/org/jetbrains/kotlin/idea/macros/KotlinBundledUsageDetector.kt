// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.macros

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.base.util.caching.getChanges
import org.jetbrains.kotlin.idea.versions.forEachAllUsedLibraries
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class KotlinBundledUsageDetector(private val project: Project, private val cs: CoroutineScope) {
    private val isKotlinBundledFound = AtomicBoolean(false)

    private fun detected() {
        if (isKotlinBundledFound.compareAndSet(/* expectedValue = */ false, /* newValue = */ true)) {
            cs.launch {
                project.messageBus.syncPublisher(TOPIC).kotlinBundledDetected()
            }
        }
    }

    internal class ModelChangeListener(private val project: Project, private val cs: CoroutineScope) : WorkspaceModelChangeListener {
        override fun changed(event: VersionedStorageChange) {
            val detectorService = project.detectorInstance
            if (detectorService.isKotlinBundledFound.get()) {
                return
            }

            val changes = event.getChanges<LibraryEntity>().ifEmpty { return }

            cs.launch {
                val isDistUsedInLibraries = changes.asSequence()
                    .mapNotNull { it.newEntity }
                    .flatMap { it.roots }
                    .any { it.url.url.isStartsWithDistPrefix }

                if (isDistUsedInLibraries) {
                    detectorService.detected()
                }
            }
        }
    }

    internal class MyStartupActivity : ProjectActivity {
        override suspend fun execute(project: Project) {
            val isUsed = readAction {
                var used = false
                project.forEachAllUsedLibraries { library ->
                    ProgressManager.checkCanceled()
                    if (library.getUrls(OrderRootType.CLASSES).any(String::isStartsWithDistPrefix)) {
                        used = true
                        return@forEachAllUsedLibraries false
                    }

                    true
                }
                used
            }

            if (isUsed) {
                project.detectorInstance.detected()
            }
        }
    }

    companion object {
        @JvmField
        @Topic.ProjectLevel
        val TOPIC = Topic(KotlinBundledUsageDetectorListener::class.java, Topic.BroadcastDirection.NONE)

        /**
         * @return
         * **false** -> **KOTLIN_BUNDLED** certainly NOT used in any JPS library.<br>
         * **true** -> **KOTLIN_BUNDLED** is potentially used in the project/module libraries
         */
        @JvmStatic
        fun isKotlinBundledPotentiallyUsedInLibraries(project: Project): Boolean {
            return project.detectorInstance.isKotlinBundledFound.get()
        }
    }
}

private val Project.detectorInstance: KotlinBundledUsageDetector get() = service()

private val kotlinDistLocationPrefixFileName by lazy {
    KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX.name
}

private val String.isStartsWithDistPrefix: Boolean
    // greatly speed-up this lookup by checking whether it *COULD* be kotlin dist folder
    get() = contains(kotlinDistLocationPrefixFileName) &&
            File(JpsPathUtil.urlToPath(this)).startsWith(KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX)
