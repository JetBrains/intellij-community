// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("ExternalBuildSystemUtils")

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Ref

val Module.externalProjectId: String?
    get() {
        ProgressManager.checkCanceled()

        return ExternalSystemApiUtil.getExternalProjectId(this)
    }

val Module.externalProjectPath: String?
    get() {
        ProgressManager.checkCanceled()

        return ExternalSystemApiUtil.getExternalProjectPath(this)
    }

/**
 * A short-lived cache to avoid computing [externalProjectId] or [externalProjectPath] multiple times.
 *
 * Supposed to be used in a single read action.
 * Not thread safe.
 *
 * Uses [Ref] to be able to cache `null` values in [projectIdCache] and [projectPathCache].
 */
internal class ModuleExternalDetailsCache {
    private val projectIdCache: MutableMap<Module, Ref<String?>> = mutableMapOf()
    private val projectPathCache: MutableMap<Module, Ref<String?>> = mutableMapOf()

    fun getExternalProjectIdOrNull(module: Module): String? {
        ProgressManager.checkCanceled()

        return projectIdCache.getOrPut(module) {
            Ref(module.externalProjectId)
        }.get()
    }

    fun getExternalProjectId(module: Module): String =
        getExternalProjectIdOrNull(module) ?: error("External project id not found for module $module")

    fun getExternalProjectPathOrNull(module: Module): String? {
        ProgressManager.checkCanceled()

        return projectPathCache.getOrPut(module) {
            Ref(module.externalProjectPath)
        }.get()
    }

    fun getExternalProjectPath(module: Module): String =
        getExternalProjectPathOrNull(module) ?: error("External project path not found for module $module")
}
