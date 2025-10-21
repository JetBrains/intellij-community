// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.roots

//import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.gradle.scripting.shared.LastModifiedFiles
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.exists

/**
 * [GradleBuildRoot] is a linked gradle build (don't confuse with gradle project and included build).
 * Each [GradleBuildRoot] may have it's own Gradle version, Java home and other settings.
 *
 * Typically, IntelliJ project have no more than one [GradleBuildRoot].
 */
sealed class GradleBuildRoot(
    private val lastModifiedFiles: LastModifiedFiles
) {
    enum class ImportingStatus {
        importing, updatingCaches, updated
    }

    val importing = AtomicReference(ImportingStatus.updated)

    abstract val pathPrefix: String

    abstract val projectRoots: Collection<String>

    val dir: VirtualFile?
        get() = LocalFileSystem.getInstance().findFileByPath(pathPrefix)

    fun saveLastModifiedFiles() {
        scriptingDebugLog { "LasModifiedFiles saved: $lastModifiedFiles" }
        LastModifiedFiles.write(dir ?: return, lastModifiedFiles)
    }

    fun areRelatedFilesChangedBefore(file: VirtualFile, lastModified: Long): Boolean =
        lastModifiedFiles.lastModifiedTimeStampExcept(file.path) < lastModified

    fun fileChanged(filePath: String, ts: Long) {
        lastModifiedFiles.fileChanged(ts, filePath)
    }

    fun isImportingInProgress(): Boolean {
        return importing.get() != ImportingStatus.updated
    }
}

sealed class WithoutScriptModels(
    settings: GradleProjectSettings,
    lastModifiedFiles: LastModifiedFiles
) : GradleBuildRoot(lastModifiedFiles) {
    final override val pathPrefix = settings.externalProjectPath!!
    final override val projectRoots = settings.modules.takeIf { it.isNotEmpty() } ?: listOf(pathPrefix)
}

/**
 * Gradle build with old Gradle version (<6.0)
 */
class Legacy(
    settings: GradleProjectSettings,
    lastModifiedFiles: LastModifiedFiles = settings.loadLastModifiedFiles() ?: LastModifiedFiles()
) : WithoutScriptModels(settings, lastModifiedFiles)

/**
 * Linked but not yet imported Gradle build.
 */
class New(
    settings: GradleProjectSettings,
    lastModifiedFiles: LastModifiedFiles = settings.loadLastModifiedFiles() ?: LastModifiedFiles()
) : WithoutScriptModels(settings, lastModifiedFiles)

/**
 * Imported Gradle build.
 * Each imported build have info about all of it's Kotlin Build Scripts.
 */
class Imported(
    override val pathPrefix: String,
    val data: GradleBuildRootData,
    lastModifiedFiles: LastModifiedFiles
) : GradleBuildRoot(lastModifiedFiles) {
    override val projectRoots: Collection<String>
        get() = data.projectRoots

    val javaHome: Path? = data.javaHome?.takeIf { it.isNotBlank() }?.let { Path.of(it) }?.takeIf { it.exists() }
}

fun GradleProjectSettings.loadLastModifiedFiles(): LastModifiedFiles? {
    val externalProjectPath = externalProjectPath ?: return null
    return loadLastModifiedFiles(externalProjectPath)
}

fun loadLastModifiedFiles(rootDirPath: String): LastModifiedFiles? {
    val vFile = LocalFileSystem.getInstance().findFileByPath(rootDirPath) ?: return null
    return LastModifiedFiles.read(vFile)
}
