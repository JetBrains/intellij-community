// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FileTypeIndex
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinVersionVerbose
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable

fun hasKotlinFilesOnlyInTests(module: Module): Boolean {
    return !hasKotlinFilesInSources(module) && FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, module.getModuleScope(true))
}

fun hasKotlinFilesInSources(module: Module): Boolean {
    return FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, module.getModuleScope(false))
}

fun Project.findAnyExternalKotlinCompilerVersion(): KotlinVersionVerbose? =
    ModuleManager.getInstance(this).modules.firstNotNullOfOrNull { it.findExternalKotlinCompilerVersion() }

fun <T> Project.syncNonBlockingReadAction(smartMode: Boolean = false, task: () -> T): T =
    ReadAction.nonBlocking<T> {
        task()
    }
        .expireWith(KotlinPluginDisposable.getInstance(this))
        .let {
            if (smartMode) it.inSmartMode(this) else it
        }
        .executeSynchronously()
