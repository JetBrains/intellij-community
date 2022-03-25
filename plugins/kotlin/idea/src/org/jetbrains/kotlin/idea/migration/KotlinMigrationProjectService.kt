// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.migration

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.notifications.showMigrationNotification
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.util.application.*
import org.jetbrains.kotlin.idea.util.runReadActionInSmartMode
import java.io.File

class KotlinMigrationProjectService(val project: Project) {
    @Volatile
    private var old: MigrationState? = null

    @Volatile
    private var importFinishListener: ((MigrationTestState?) -> Unit)? = null

    class MigrationTestState(val migrationInfo: MigrationInfo?, val hasApplicableTools: Boolean)

    @TestOnly
    fun setImportFinishListener(newListener: ((MigrationTestState?) -> Unit)?) {
        synchronized(this) {
            if (newListener != null && importFinishListener != null) {
                importFinishListener!!.invoke(null)
            }

            importFinishListener = newListener
        }
    }

    private fun notifyFinish(migrationInfo: MigrationInfo?, hasApplicableTools: Boolean) {
        importFinishListener?.invoke(MigrationTestState(migrationInfo, hasApplicableTools))
    }

    fun onImportAboutToStart() {
        if (!CodeMigrationToggleAction.isEnabled(project) || !hasChangesInProjectFiles(project)) {
            old = null
            return
        }

        old = MigrationState.build(project)
    }

    fun onImportFinished() {
        if (!CodeMigrationToggleAction.isEnabled(project) || old == null) {
            notifyFinish(null, false)
            return
        }

        executeOnPooledThread {
            var migrationInfo: MigrationInfo? = null
            var hasApplicableTools = false

            try {
                val new = project.runReadActionInSmartMode {
                    MigrationState.build(project)
                }

                val localOld = old.also {
                    old = null
                } ?: return@executeOnPooledThread

                migrationInfo = prepareMigrationInfo(localOld, new) ?: return@executeOnPooledThread

                if (applicableMigrationTools(migrationInfo).isEmpty()) {
                    hasApplicableTools = false
                    return@executeOnPooledThread
                } else {
                    hasApplicableTools = true
                }

                if (isUnitTestMode()) {
                    return@executeOnPooledThread
                }

                invokeLater {
                    showMigrationNotification(project, migrationInfo)
                }
            } finally {
                notifyFinish(migrationInfo, hasApplicableTools)
            }
        }
    }

    companion object {
        fun getInstance(project: Project): KotlinMigrationProjectService = project.getServiceSafe()

        private fun prepareMigrationInfo(old: MigrationState?, new: MigrationState?): MigrationInfo? {
            if (old == null || new == null) return null

            if (old.apiVersion < new.apiVersion || old.languageVersion < new.languageVersion) {
                return MigrationInfo(
                    oldApiVersion = old.apiVersion,
                    newApiVersion = new.apiVersion,
                    oldLanguageVersion = old.languageVersion,
                    newLanguageVersion = new.languageVersion,
                )
            }

            return null
        }

        private fun hasChangesInProjectFiles(project: Project): Boolean {
            if (ProjectLevelVcsManagerEx.getInstance(project).allVcsRoots.isEmpty()) {
                return true
            }

            val checkedFiles = HashSet<File>()

            project.basePath?.let { projectBasePath ->
                checkedFiles.add(File(projectBasePath))
            }

            val changedFiles = ChangeListManager.getInstance(project).affectedPaths
            for (changedFile in changedFiles) {
                when (changedFile.extension) {
                    "gradle" -> return true
                    "properties" -> return true
                    "kts" -> return true
                    "iml" -> return true
                    "xml" -> {
                        if (changedFile.name == "pom.xml") return true
                        val parentDir = changedFile.parentFile
                        if (parentDir.isDirectory && parentDir.name == Project.DIRECTORY_STORE_FOLDER) {
                            return true
                        }
                    }

                    "kt", "java", "groovy" -> {
                        val dirs: Sequence<File> = generateSequence(changedFile) { it.parentFile }
                            .drop(1) // Drop original file
                            .takeWhile { it.isDirectory }

                        val isInBuildSrc = dirs
                            .takeWhile { checkedFiles.add(it) }
                            .any { it.name == BUILD_SRC_FOLDER_NAME }

                        if (isInBuildSrc) {
                            return true
                        }
                    }
                }
            }

            return false
        }
    }
}

private class MigrationState(
    val apiVersion: ApiVersion,
    val languageVersion: LanguageVersion,
) {
    companion object {
        fun build(project: Project): MigrationState = runReadAction {
            var maxApiVersion: ApiVersion? = null
            var maxLanguageVersion: LanguageVersion? = null

            for (module in ModuleManager.getInstance(project).modules) {
                if (!module.isKotlinModule()) {
                    // Otherwise, project compiler settings will give unreliable maximum for compiler settings
                    continue
                }

                val languageVersionSettings = module.languageVersionSettings

                if (maxApiVersion == null || languageVersionSettings.apiVersion > maxApiVersion) {
                    maxApiVersion = languageVersionSettings.apiVersion
                }

                if (maxLanguageVersion == null || languageVersionSettings.languageVersion > maxLanguageVersion) {
                    maxLanguageVersion = languageVersionSettings.languageVersion
                }
            }

            val bundledKotlinVersion = KotlinPluginLayout.instance.standaloneCompilerVersion

            MigrationState(
                apiVersion = maxApiVersion ?: bundledKotlinVersion.apiVersion,
                languageVersion = maxLanguageVersion ?: bundledKotlinVersion.languageVersion,
            )
        }
    }
}

class MigrationInfo(
    val oldApiVersion: ApiVersion,
    val newApiVersion: ApiVersion,
    val oldLanguageVersion: LanguageVersion,
    val newLanguageVersion: LanguageVersion,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MigrationInfo) return false

        if (oldApiVersion != other.oldApiVersion) return false
        if (newApiVersion != other.newApiVersion) return false
        if (oldLanguageVersion != other.oldLanguageVersion) return false
        if (newLanguageVersion != other.newLanguageVersion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = oldApiVersion.hashCode()
        result = 31 * result + newApiVersion.hashCode()
        result = 31 * result + oldLanguageVersion.hashCode()
        result = 31 * result + newLanguageVersion.hashCode()
        return result
    }
}

fun MigrationInfo.isLanguageVersionUpdate(old: LanguageVersion, new: LanguageVersion): Boolean {
    return oldLanguageVersion <= old && newLanguageVersion >= new
}


private const val BUILD_SRC_FOLDER_NAME = "buildSrc"

private fun Module.isKotlinModule(): Boolean {
    if (isDisposed) return false

    if (KotlinFacet.get(this) != null) {
        return true
    }

    // This code works only for Maven and Gradle import, and it's expected that Kotlin facets are configured for
    // all modules with external system.
    return false
}