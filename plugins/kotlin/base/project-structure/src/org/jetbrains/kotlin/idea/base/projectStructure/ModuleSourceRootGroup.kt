// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("ModuleSourceRootGroupUtils")

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules

class ModuleSourceRootGroup(
    val baseModule: Module,
    val sourceRootModules: List<Module>
)

class ModuleSourceRootMap private constructor(val modules: Collection<Module>) {
    private val baseModuleByExternalPath: Map<String, Module>

    private val allModulesByExternalPath: Map<String, List<Module>>

    constructor(project: Project) : this(project.modules.asList())

    init {
        val cache = ModuleExternalDetailsCache()

        allModulesByExternalPath = modules
            .asSequence()
            .filter { cache.getExternalProjectPathOrNull(it) != null && cache.getExternalProjectIdOrNull(it) != null }
            .groupBy { cache.getExternalProjectPath(it) }

        baseModuleByExternalPath = allModulesByExternalPath
            .mapValues { (_, modules) ->
                modules.reduce { m1, m2 ->
                    if (isSourceRootPrefix(cache.getExternalProjectId(m2), cache.getExternalProjectId(m1))) m2 else m1
                }
            }
    }

    fun groupByBaseModules(modules: Collection<Module>): List<ModuleSourceRootGroup> {
        return modules
            .groupBy { module ->
                val externalPath = module.externalProjectPath
                if (externalPath == null) module else (baseModuleByExternalPath[externalPath] ?: module)
            }
            .map { (module, sourceRootModules) ->
                ModuleSourceRootGroup(
                    module,
                    if (sourceRootModules.size > 1) sourceRootModules - module else sourceRootModules
                )
            }
    }

    fun toModuleGroup(module: Module): ModuleSourceRootGroup = groupByBaseModules(listOf(module)).single()

    fun getWholeModuleGroup(module: Module): ModuleSourceRootGroup {
        val externalPath = module.externalProjectPath
        val baseModule = (if (externalPath != null) baseModuleByExternalPath[externalPath] else null) ?: return ModuleSourceRootGroup(
            module,
            listOf(module)
        )

        val externalPathModules = allModulesByExternalPath[externalPath] ?: listOf()
        return ModuleSourceRootGroup(baseModule, if (externalPathModules.size > 1) externalPathModules - module else externalPathModules)
    }
}

fun ModuleSourceRootGroup.allModules(): Set<Module> {
    val result = LinkedHashSet<Module>()
    result.add(baseModule)
    result.addAll(sourceRootModules)
    return result
}

fun Module.toModuleGroup() = ModuleSourceRootMap(project).toModuleGroup(this)

fun Module.getWholeModuleGroup() = ModuleSourceRootMap(project).getWholeModuleGroup(this)

fun List<ModuleSourceRootGroup>.exclude(excludeModules: Collection<Module>): List<ModuleSourceRootGroup> {
    return mapNotNull {
        if (it.baseModule in excludeModules)
            null
        else {
            val remainingSourceRootModules = it.sourceRootModules - excludeModules.toSet()
            if (remainingSourceRootModules.isEmpty())
                null
            else
                ModuleSourceRootGroup(it.baseModule, remainingSourceRootModules)
        }
    }
}

private fun isSourceRootPrefix(externalId: String, previousModuleExternalId: String): Boolean {
    return externalId.length < previousModuleExternalId.length && previousModuleExternalId.startsWith(externalId)
}