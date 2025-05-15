// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.roots

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.gradle.scripting.shared.LastModifiedFiles
import org.jetbrains.kotlin.gradle.scripting.shared.getGradleVersion
import org.jetbrains.kotlin.gradle.scripting.shared.importing.KotlinDslGradleBuildSync
import org.jetbrains.kotlin.gradle.scripting.shared.kotlinDslScriptsModelImportSupported
import org.jetbrains.kotlin.idea.core.script.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.scriptingErrorLog
import org.jetbrains.kotlin.idea.core.script.scriptingInfoLog
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

fun interface GradleBuildRootsLocatorFactory {
    fun getInstance(): GradleBuildRootsLocator
}

abstract class GradleBuildRootsLocator(private val project: Project, private val coroutineScope: CoroutineScope) {
    val roots: GradleBuildRootIndex = GradleBuildRootIndex(project)

    fun fileChanged(filePath: String, ts: Long = System.currentTimeMillis()) {
        findAffectedFileRoot(filePath)?.fileChanged(filePath, ts)
        scheduleModifiedFilesCheck(filePath)
    }

    private val modifiedFilesCheckScheduled = AtomicBoolean()
    private val modifiedFiles = ConcurrentLinkedQueue<String>()

    private fun scheduleModifiedFilesCheck(filePath: String) {
        modifiedFiles.add(filePath)
        if (modifiedFilesCheckScheduled.compareAndSet(false, true)) {
            coroutineScope.launch {
                if (modifiedFilesCheckScheduled.compareAndSet(true, false)) {
                    checkModifiedFiles()
                }
            }
        }
    }

    private fun checkModifiedFiles() {
        updateNotifications(restartAnalyzer = false) { true }

        roots.list.forEach {
            it.saveLastModifiedFiles()
        }

        // process modifiedFiles queue
        while (true) {
            val file = modifiedFiles.poll() ?: break

            // detect gradle version change
            val buildDir = findGradleWrapperPropertiesBuildDir(file)
            if (buildDir != null) {
                actualizeBuildRoot(buildDir, null)
            }
        }
    }

    abstract fun getScriptInfo(localPath: String): GradleScriptInfo?

    abstract fun loadLinkedRoot(settings: GradleProjectSettings, version: String): GradleBuildRoot

    fun getAllRoots(): Collection<GradleBuildRoot> = roots.list

    fun getBuildRootByWorkingDir(gradleWorkingDir: String): GradleBuildRoot? =
        roots.getBuildByRootDir(gradleWorkingDir)

    fun getScriptInfo(file: VirtualFile): GradleScriptInfo? =
        getScriptInfo(file.localPath)

    private val VirtualFile.localPath
        get() = path

    private val gradleWrapperEnding = "/gradle/wrapper/gradle-wrapper.properties"

    fun maybeAffectedGradleProjectFile(filePath: String): Boolean =
        filePath.endsWith("/gradle.properties") ||
                filePath.endsWith("/gradle.local") ||
                filePath.endsWith(gradleWrapperEnding) ||
                filePath.endsWith(".gradle.kts")

    fun isAffectedGradleProjectFile(filePath: String): Boolean =
        findAffectedFileRoot(filePath) != null ||
                roots.isStandaloneScript(filePath)

    fun findAffectedFileRoot(filePath: String): GradleBuildRoot? {
        if (filePath.endsWith("/gradle.properties") ||
            filePath.endsWith("/gradle.local")
        ) {
            return roots.getBuildByProjectDir(filePath.substringBeforeLast("/"))
        }

        return findGradleWrapperPropertiesBuildDir(filePath)?.let { roots.getBuildByRootDir(it) }
            ?: findScriptBuildRoot(filePath, searchNearestLegacy = false)?.root
    }

    fun findGradleWrapperPropertiesBuildDir(filePath: String): String? {
        if (filePath.endsWith(gradleWrapperEnding)) {
            return filePath.substring(0, filePath.length - gradleWrapperEnding.length)
        }

        return null
    }

    fun update(sync: KotlinDslGradleBuildSync) {
        val oldRoot = actualizeBuildRoot(sync.workingDir, sync.gradleVersion) ?: return

        try {
            val newRoot = updateRoot(oldRoot, sync)
            if (newRoot == null) {
                markImportingInProgress(sync.workingDir, false)
                return
            }

            add(newRoot)
        } catch (e: Exception) {
            markImportingInProgress(sync.workingDir, false)
            scriptingErrorLog("Couldn't update Gradle build root: ${oldRoot.pathPrefix}", e)
            return
        }
    }

    fun markImportingInProgress(workingDir: String, inProgress: Boolean = true) {
        actualizeBuildRoot(
            workingDir,
            null
        )?.importing?.set(if (inProgress) GradleBuildRoot.ImportingStatus.importing else GradleBuildRoot.ImportingStatus.updated)
        updateNotifications { it.startsWith(workingDir) }
    }

    abstract fun add(newRoot: GradleBuildRoot)

    /**
     * Check that root under [workingDir] in sync with it's [GradleProjectSettings].
     * Actually this should be true, but we may miss some change events.
     * For that cases we are rechecking this on each Gradle Project sync (importing/reimporting)
     */
    protected fun actualizeBuildRoot(workingDir: String, gradleVersion: String?): GradleBuildRoot? {
        val actualSettings = getGradleProjectSettings(workingDir)
        val buildRoot = getBuildRootByWorkingDir(workingDir)

        val version = gradleVersion ?: actualSettings?.let { getGradleVersion(project, it) }
        return when {
            buildRoot != null -> {
                when {
                    !buildRoot.checkActual(version) -> reloadBuildRoot(workingDir, version)
                    else -> buildRoot
                }
            }

            actualSettings != null && version != null -> {
                loadLinkedRoot(actualSettings, version)
            }

            else -> null
        }
    }

    private fun GradleBuildRoot.checkActual(version: String?): Boolean {
        if (version == null) return false

        val knownAsSupported = this !is Legacy
        val shouldBeSupported = kotlinDslScriptsModelImportSupported(version)
        return knownAsSupported == shouldBeSupported
    }

    fun reloadBuildRoot(rootPath: String, version: String?): GradleBuildRoot? {
        val settings = getGradleProjectSettings(rootPath)
        if (settings == null) {
            remove(rootPath)
            return null
        } else {
            val gradleVersion = version ?: getGradleVersion(project, settings)
            val newRoot = loadLinkedRoot(settings, gradleVersion)
            add(newRoot)
            return newRoot
        }
    }

    abstract fun remove(rootPath: String)

    protected fun removeData(rootPath: String) {
        val buildRoot = LocalFileSystem.getInstance().findFileByPath(rootPath)
        if (buildRoot != null) {
            GradleBuildRootDataSerializer.getInstance().remove(buildRoot)
            LastModifiedFiles.remove(buildRoot)
        }
    }

    fun isConfigurationOutOfDate(file: VirtualFile): Boolean {
        val script = getScriptInfo(file) ?: return false
        if (script.buildRoot.isImportingInProgress()) return false
        return !script.model.inputs.isUpToDate(project, file)
    }

    abstract fun updateNotifications(
        restartAnalyzer: Boolean = true,
        shouldUpdatePath: (String) -> Boolean
    )

    private fun getGradleProjectSettings(workingDir: String): GradleProjectSettings? {
        return (ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID) as GradleSettings)
            .getLinkedProjectSettings(workingDir)
    }

    private fun updateRoot(oldRoot: GradleBuildRoot, sync: KotlinDslGradleBuildSync): Imported? {
        // fast path for linked gradle builds without .gradle.kts support
        if (sync.models.isEmpty()) {
            if (oldRoot is Imported && oldRoot.data.models.isEmpty()) return null
        }

        if (oldRoot is Legacy) return null

        scriptingDebugLog { "gradle project info after import: $sync" }

        // TODO: can gradleHome be null, what to do in this case
        val gradleHome = sync.gradleHome
        if (gradleHome == null) {
            scriptingInfoLog("Cannot find valid gradle home with version = ${sync.gradleVersion}, script models cannot be saved")
            return null
        }

        oldRoot.importing.set(GradleBuildRoot.ImportingStatus.updatingCaches)

        scriptingDebugLog { "save script models after import: ${sync.models}" }

        val newData = GradleBuildRootData(sync.creationTimestamp, sync.projectRoots, gradleHome, sync.javaHome, sync.models)
        val mergedData = if (sync.failed && oldRoot is Imported) merge(oldRoot.data, newData) else newData

        val newRoot = tryCreateImportedRoot(sync.workingDir, LastModifiedFiles()) { mergedData } ?: return null
        val buildRootDir = newRoot.dir ?: return null

        GradleBuildRootDataSerializer.getInstance().write(buildRootDir, mergedData)
        newRoot.saveLastModifiedFiles()

        return newRoot
    }

    protected fun tryCreateImportedRoot(
        externalProjectPath: String,
        lastModifiedFiles: LastModifiedFiles = loadLastModifiedFiles(externalProjectPath) ?: LastModifiedFiles(),
        dataProvider: (buildRoot: VirtualFile) -> GradleBuildRootData?
    ): Imported? {
        try {
            val buildRoot = VfsUtil.findFile(Paths.get(externalProjectPath), true) ?: return null
            val data = dataProvider(buildRoot) ?: return null

            return Imported(externalProjectPath, data, lastModifiedFiles)
        } catch (e: Exception) {
            when (e) {
                is ControlFlowException -> throw e
                else -> scriptingErrorLog("Cannot load script configurations from file attributes for $externalProjectPath", e)
            }
            return null
        }
    }

    private fun merge(old: GradleBuildRootData, new: GradleBuildRootData): GradleBuildRootData {
        val roots = old.projectRoots.toMutableSet()
        roots.addAll(new.projectRoots)

        val models = old.models.associateByTo(mutableMapOf()) { it.file }
        new.models.associateByTo(models) { it.file }

        return GradleBuildRootData(new.importTs, roots, new.gradleHome, new.javaHome, models.values)
    }

    @Suppress("EnumEntryName")
    enum class NotificationKind {
        dontCare, // one of: imported,
        legacy, // inside linked legacy gradle build
        legacyOutside, // gradle 6-: suggest to mark as standalone
        outsideAnything, // suggest link related gradle build or just say that there is no one
        wasNotImportedAfterCreation, // project not yet imported after this file was created
        notEvaluatedInLastImport, // all other scripts, suggest to sync or mark as standalone
        standalone,
        standaloneLegacy
    }

    /**
     * Timestamp of an moment when script file was discovered (indexed).
     * Used to detect if that script was existed at the moment of import
     */
    open fun getScriptFirstSeenTs(path: String): Long {
        val nioPath = FileSystems.getDefault().getPath(path)
        return Files.readAttributes(nioPath, BasicFileAttributes::class.java).creationTime()?.toMillis()
            ?: Long.MAX_VALUE
    }

    open fun updateStandaloneScripts(update: StandaloneScriptsUpdater.() -> Unit) {}

    inner class ScriptUnderRoot(
        val filePath: String,
        val root: GradleBuildRoot?,
        val script: GradleScriptInfo? = null,
        val standalone: Boolean = false,
        val nearest: GradleBuildRoot? = root
    ) {
        val notificationKind: NotificationKind
            get() = when {
                isImported -> NotificationKind.dontCare
                standalone -> when (nearest) {
                    is Legacy -> NotificationKind.standaloneLegacy
                    else -> NotificationKind.standalone
                }

                nearest == null -> NotificationKind.outsideAnything
                importing -> NotificationKind.dontCare
                else -> when (nearest) {
                    is Legacy -> when (root) {
                        null -> NotificationKind.legacyOutside
                        else -> NotificationKind.legacy
                    }

                    is New -> NotificationKind.wasNotImportedAfterCreation
                    is Imported -> when {
                        wasImportedAndNotEvaluated -> NotificationKind.notEvaluatedInLastImport
                        else -> NotificationKind.wasNotImportedAfterCreation
                    }
                }
            }

        private val importing: Boolean
            get() = nearest != null && nearest.isImportingInProgress()

        private val isImported: Boolean
            get() = script != null

        private val wasImportedAndNotEvaluated: Boolean
            get() = nearest is Imported &&
                    getScriptFirstSeenTs(filePath) < nearest.data.importTs

        override fun toString(): String {
            return "ScriptUnderRoot(root=$root, script=$script, standalone=$standalone, nearest=$nearest)"
        }
    }

    fun findScriptBuildRoot(gradleKtsFile: VirtualFile): ScriptUnderRoot? =
        findScriptBuildRoot(gradleKtsFile.path)

    fun findScriptBuildRoot(filePath: String, searchNearestLegacy: Boolean = true): ScriptUnderRoot? {
        if (project.isDisposed) {
            // This is not really correct as this check should be under a read/write action. Still, better than nothing.
            return null
        }

        if (!filePath.endsWith(".gradle.kts")) return null

        val scriptInfo = getScriptInfo(filePath)
        scriptInfo?.buildRoot?.let {
            return ScriptUnderRoot(filePath, it, scriptInfo)
        }

        // stand-alone scripts
        roots.getStandaloneScriptRoot(filePath)?.let {
            return ScriptUnderRoot(filePath, it, standalone = true)
        }

        if (filePath.endsWith("/build.gradle.kts") ||
            filePath.endsWith("/settings.gradle.kts") ||
            filePath.endsWith("/init.gradle.kts")
        ) {
            // build|settings|init.gradle.kts scripts should be located near gradle project root only
            roots.getBuildByProjectDir(filePath.substringBeforeLast("/"))?.let {
                return ScriptUnderRoot(filePath, it)
            }
        }

        // other scripts: "included", "precompiled" scripts, scripts in unlinked projects,
        // or just random files with ".gradle.kts" ending OR scripts those Gradle has not provided
        val nearest =
            if (searchNearestLegacy) roots.findNearestRoot(filePath)
            else null

        return ScriptUnderRoot(filePath, null, nearest = nearest)
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): GradleBuildRootsLocator = project.service()
    }
}