// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.NonClasspathDirectoriesScope.compose
import org.jetbrains.kotlin.idea.core.script.configuration.utils.ScriptClassRootsStorage
import org.jetbrains.kotlin.idea.core.script.k2.ScriptClassPathUtil
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import java.lang.ref.Reference
import java.lang.ref.SoftReference

class ScriptClassRootsCache(
    val scripts: Map<String, LightScriptInfo>,
    private val classes: Set<String>,
    private val sources: Set<String>,
    val customDefinitionsUsed: Boolean,
    val sdks: ScriptSdks,
    private val classpathVfsHint: MutableMap<String, VirtualFile?>?,
) {
    companion object {
        val EMPTY: ScriptClassRootsCache = ScriptClassRootsCache(
            mapOf(), setOf(), setOf(), true,
            ScriptSdks(mapOf(), setOf(), setOf()),
            null
        )
    }

    fun withUpdatedSdks(newSdks: ScriptSdks): ScriptClassRootsCache =
        ScriptClassRootsCache(scripts, classes, sources, customDefinitionsUsed, newSdks, classpathVfsHint)

    fun builder(project: Project): ScriptClassRootsBuilder {
        return ScriptClassRootsBuilder(
            project,
            classes.toMutableSet(),
            sources.toMutableSet(),
            scripts.toMutableMap()
        ).also { builder ->
            if (customDefinitionsUsed) {
                builder.useCustomScriptDefinition()
            }
            builder.sdks.addAll(sdks)
            builder.withClasspathVfsHint(classpathVfsHint ?: mutableMapOf())
        }
    }

    abstract class LightScriptInfo(val definition: ScriptDefinition?) {
        @Volatile
        var heavyCache: Reference<HeavyScriptInfo>? = null

        abstract fun buildConfiguration(): ScriptCompilationConfigurationWrapper?
    }

    class DirectScriptInfo(val result: ScriptCompilationConfigurationWrapper) : LightScriptInfo(null) {
        override fun buildConfiguration(): ScriptCompilationConfigurationWrapper = result
    }

    class HeavyScriptInfo(
        val scriptConfiguration: ScriptCompilationConfigurationWrapper,
        val classFiles: List<VirtualFile>,
        val classFilesScope: GlobalSearchScope,
        val sdk: Sdk?
    )

    fun scriptsPaths(): Set<String> = scripts.keys

    fun getLightScriptInfo(file: String): LightScriptInfo? = scripts[file]

    fun contains(file: VirtualFile): Boolean = file.path in scripts

    private fun getHeavyScriptInfo(file: String): HeavyScriptInfo? {
        val lightScriptInfo = getLightScriptInfo(file) ?: return null
        val heavy0 = lightScriptInfo.heavyCache?.get()
        if (heavy0 != null) return heavy0

        return runReadAction {
            // The lock ^ is needed to define the order of acquisition: (read, lightScriptInfo), thus preventing possible deadlock.
            synchronized(lightScriptInfo) {
                val heavy1 = lightScriptInfo.heavyCache?.get()
                if (heavy1 != null) return@runReadAction heavy1
                val heavy2 = computeHeavy(lightScriptInfo) // might require read-lock inside
                lightScriptInfo.heavyCache = SoftReference(heavy2)
                return@runReadAction heavy2
            }
        }
    }

    private fun computeHeavy(lightScriptInfo: LightScriptInfo): HeavyScriptInfo? {
        val configuration = lightScriptInfo.buildConfiguration() ?: return null

        val vfsRoots = configuration.dependenciesClassPath.mapNotNull { ScriptClassPathUtil.findVirtualFile(it.path) }
        val sdk = sdks[SdkId(configuration.javaHome?.toPath())]

        fun heavyInfoForRoots(roots: List<VirtualFile>): HeavyScriptInfo {
            return HeavyScriptInfo(configuration, roots, compose(roots), sdk)
        }

        return if (sdk == null) {
            heavyInfoForRoots(vfsRoots)
        } else {
            val sdkClasses = sdk.rootProvider.getFiles(OrderRootType.CLASSES).toList()
            heavyInfoForRoots(sdkClasses + vfsRoots)
        }
    }

    val firstScriptSdk: Sdk?
        get() = sdks.first

    val allDependenciesClassFiles: Set<VirtualFile>

    val allDependenciesSources: Set<VirtualFile>

    init {
        fun String.toVFile(): VirtualFile? {
            return if (classpathVfsHint?.containsKey(this) == true) {
                classpathVfsHint[this]
            } else {
                ScriptClassPathUtil.findVirtualFile(this).also { vFile ->
                    classpathVfsHint?.put(this, vFile)
                }
            }
        }

        allDependenciesClassFiles = mutableSetOf<VirtualFile>().also { result ->
            classes.mapNotNullTo(result) { it.toVFile() }
        }

        allDependenciesSources = mutableSetOf<VirtualFile>().also { result ->
            sources.mapNotNullTo(result) { it.toVFile() }
        }
    }

    val allDependenciesClassFilesScope: GlobalSearchScope = compose(allDependenciesClassFiles.toList() + sdks.nonIndexedClassRoots)

    val allDependenciesSourcesScope: GlobalSearchScope = compose(allDependenciesSources.toList() + sdks.nonIndexedSourceRoots)

    fun getScriptConfiguration(file: VirtualFile): ScriptCompilationConfigurationWrapper? =
        getHeavyScriptInfo(file.path)?.scriptConfiguration

    fun getScriptSdk(path: String): Sdk? =
        getHeavyScriptInfo(path)?.sdk

    fun getScriptSdk(file: VirtualFile): Sdk? =
        getScriptSdk(file.path)

    fun getScriptDependenciesClassFilesScope(file: VirtualFile): GlobalSearchScope =
        getHeavyScriptInfo(file.path)?.classFilesScope ?: GlobalSearchScope.EMPTY_SCOPE

    fun getScriptDependenciesClassFiles(file: VirtualFile): List<VirtualFile> =
        getHeavyScriptInfo(file.path)?.classFiles ?: emptyList()

    fun getScriptDependenciesSourceFiles(file: VirtualFile): List<VirtualFile> {
        return getHeavyScriptInfo(file.path)?.scriptConfiguration?.dependenciesSources?.mapNotNull { file ->
            ScriptClassPathUtil.findVirtualFile(file.path)
        } ?: emptyList()
    }

    fun diff(old: ScriptClassRootsCache): Updates =
        when (old) {
            EMPTY -> FullUpdate(this)
            this -> NotChanged(this)
            else -> IncrementalUpdates(
                cache = this,
                hasNewRoots = this.hasNewRoots(old),
                hasOldRoots = old.hasNewRoots(this),
                updatedScripts = getChangedScripts(old)
            )
        }

    private fun hasNewRoots(old: ScriptClassRootsCache): Boolean {
        val oldClassRoots = old.allDependenciesClassFiles.toSet()
        val oldSourceRoots = old.allDependenciesSources.toSet()

        return allDependenciesClassFiles.any { it !in oldClassRoots } ||
                allDependenciesSources.any { it !in oldSourceRoots } ||
                old.sdks != sdks
    }

    private fun getChangedScripts(old: ScriptClassRootsCache): Set<String> {
        val changed = mutableSetOf<String>()

        scripts.forEach {
            if (old.scripts[it.key] != it.value) {
                changed.add(it.key)
            }
        }

        old.scripts.forEach {
            if (it.key !in scripts) {
                changed.add(it.key)
            }
        }

        return changed
    }

    interface Updates {
        val cache: ScriptClassRootsCache
        val changed: Boolean
        val hasNewRoots: Boolean
        val hasUpdatedScripts: Boolean
        fun isScriptChanged(scriptPath: String): Boolean
    }

    class IncrementalUpdates(
        override val cache: ScriptClassRootsCache,
        override val hasNewRoots: Boolean,
        private val hasOldRoots: Boolean,
        val updatedScripts: Set<String>
    ) : Updates {
        override val hasUpdatedScripts: Boolean get() = updatedScripts.isNotEmpty()
        override fun isScriptChanged(scriptPath: String): Boolean = scriptPath in updatedScripts

        override val changed: Boolean
            get() = hasNewRoots || updatedScripts.isNotEmpty() || hasOldRoots
    }

    class FullUpdate(override val cache: ScriptClassRootsCache) : Updates {
        override val changed: Boolean get() = true
        override val hasUpdatedScripts: Boolean get() = true
        override fun isScriptChanged(scriptPath: String): Boolean = true

        override val hasNewRoots: Boolean
            get() =
                cache.allDependenciesClassFiles.isNotEmpty() ||
                        cache.allDependenciesSources.isNotEmpty() ||
                        cache.sdks.nonIndexedClassRoots.isNotEmpty() ||
                        cache.sdks.nonIndexedSourceRoots.isNotEmpty()
    }

    class NotChanged(override val cache: ScriptClassRootsCache) : Updates {
        override val changed: Boolean get() = false
        override val hasNewRoots: Boolean get() = false
        override val hasUpdatedScripts: Boolean get() = false
        override fun isScriptChanged(scriptPath: String): Boolean = false
    }
}

class ScriptCacheDependenciesFileInvalidator : CachesInvalidator() {
    override fun invalidateCaches() {
        ProjectManager.getInstance().openProjects.forEach {
            ScriptClassRootsStorage.getInstance(it).clear()
        }
    }
}
