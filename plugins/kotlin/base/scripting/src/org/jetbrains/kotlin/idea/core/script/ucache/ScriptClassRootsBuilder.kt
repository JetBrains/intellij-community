// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.configuration.utils.ScriptClassRootsStorage
import org.jetbrains.kotlin.idea.core.script.logger
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import kotlin.io.path.absolutePathString
import kotlin.io.path.notExists

class ScriptClassRootsBuilder(
    val project: Project,
    private val classes: MutableSet<String> = mutableSetOf(),
    private val sources: MutableSet<String> = mutableSetOf(),
    private val scripts: MutableMap<String, ScriptClassRootsCache.LightScriptInfo> = mutableMapOf()
) {
    val sdks: ScriptSdksBuilder = ScriptSdksBuilder(project)

    private var customDefinitionsUsed: Boolean = false
    private var shouldWarnAboutDependenciesExistence: Boolean = true
    private var classpathVfsHint: MutableMap<String, VirtualFile?>? = null

    constructor(builder: ScriptClassRootsBuilder) : this(
        builder.project,
        builder.classes.toMutableSet(),
        builder.sources.toMutableSet(),
        builder.scripts.toMutableMap()
    ) {
        sdks.sdks.putAll(builder.sdks.sdks)
    }

    fun build(): ScriptClassRootsCache =
        ScriptClassRootsCache(
            scripts, classes, sources,
            customDefinitionsUsed, sdks.build(),
            classpathVfsHint
        )

    fun useCustomScriptDefinition() {
        customDefinitionsUsed = true
    }

    fun warnAboutDependenciesExistence(enabled: Boolean) {
        shouldWarnAboutDependenciesExistence = enabled
    }

    fun withClasspathVfsHint(hint: MutableMap<String, VirtualFile?>?) {
        classpathVfsHint = hint
    }

    fun add(
        vFile: VirtualFile,
        configuration: ScriptCompilationConfigurationWrapper
    ) {
        sdks.addSdk(configuration.javaHome?.toPath())

        configuration.dependenciesClassPath.forEach { file ->
            val path = file.toPath()
            val absolutePath = path.absolutePathString()
            if (shouldWarnAboutDependenciesExistence && path.notExists()) {
                logger.warn("configuration dependency classpath $absolutePath does not exist")
            }

            classes.add(absolutePath)
        }

        configuration.dependenciesSources.forEach { file ->
            val path = file.toPath()
            val absolutePath = path.absolutePathString()
            if (shouldWarnAboutDependenciesExistence && path.notExists()) {
                logger.warn("configuration dependency sources $absolutePath does not exist")
            }

            sources.add(absolutePath)
        }

        scripts[vFile.path] = ScriptClassRootsCache.DirectScriptInfo(configuration)

        useCustomScriptDefinition()
    }

    fun addCustom(
        path: String,
        scriptClassesRoots: Collection<String>,
        sourceSourcesRoots: Collection<String>,
        info: ScriptClassRootsCache.LightScriptInfo
    ) {
        classes.addAll(scriptClassesRoots)
        sources.addAll(sourceSourcesRoots)
        scripts[path] = info
    }

    fun addTemplateClassesRoots(classesRoots: Collection<String>) {
        classes.addAll(classesRoots)
    }

    fun addSources(sourcesRoots: Collection<String>) {
        sources.addAll(sourcesRoots)
    }

    @Deprecated("Don't use, used only from DefaultScriptingSupport for saving to storage")
    fun add(other: ScriptClassRootsBuilder) {
        classes.addAll(other.classes)
        sources.addAll(other.sources)
        sdks.addAll(other.sdks)
        scripts.putAll(other.scripts)
    }

    fun toStorage(storage: ScriptClassRootsStorage) {
        storage.classpath = classes
        storage.sources = sources
        sdks.toStorage(storage)
    }

    companion object {
        fun fromStorage(project: Project, storage: ScriptClassRootsStorage): ScriptClassRootsBuilder = ScriptClassRootsBuilder(
            project,
            storage.classpath.toMutableSet(),
            storage.sources.toMutableSet(),
        ).also {
            it.sdks.fromStorage(storage)
        }
    }
}
