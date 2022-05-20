// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.search.FileTypeIndex
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.psi.UserDataProperty

fun hasKotlinFilesOnlyInTests(module: Module): Boolean {
    return !hasKotlinFilesInSources(module) && FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, module.getModuleScope(true))
}

fun hasKotlinFilesInSources(module: Module): Boolean {
    return FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, module.getModuleScope(false))
}

var Module.externalCompilerVersion: String? by UserDataProperty(Key.create("EXTERNAL_COMPILER_VERSION"))

fun findLatestExternalKotlinCompilerVersion(project: Project): IdeKotlinVersion? {
    return findExternalKotlinCompilerVersions(project).maxOrNull()
}

fun findExternalKotlinCompilerVersions(project: Project): Set<IdeKotlinVersion> {
    val result = LinkedHashSet<IdeKotlinVersion>()
    var hasJpsModules = false

    runReadAction {
        for (module in ModuleManager.getInstance(project).modules) {
            if (module.buildSystemType == BuildSystemType.JPS) {
                if (!hasJpsModules) hasJpsModules = true
            } else {
                val externalVersion = module.externalCompilerVersion?.let(IdeKotlinVersion::opt)
                if (externalVersion != null) {
                    result.add(externalVersion)
                }
            }
        }
    }

    if (hasJpsModules) {
        val projectGlobalVersion = KotlinJpsPluginSettings.jpsVersion(project)?.let(IdeKotlinVersion::opt)
        if (projectGlobalVersion != null) {
            result.add(projectGlobalVersion)
        }
    }

    return result
}

fun <T> Project.syncNonBlockingReadAction(smartMode: Boolean = false, task: () -> T): T =
    ReadAction.nonBlocking<T> {
        task()
    }
        .expireWith(KotlinPluginDisposable.getInstance(this))
        .let {
            if (smartMode) it.inSmartMode(this) else it
        }
        .executeSynchronously()
