// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.util.projectStructure.allModules

class ModuleSourceRootGroup(
    val baseModule: Module,
    val sourceRootModules: List<Module>
)

class ModuleSourceRootMap(val modules: Collection<Module>) {
    private val baseModuleByExternalPath: Map<String, Module>

    @Suppress("JoinDeclarationAndAssignment")
    private val allModulesByExternalPath: Map<String, List<Module>>

    constructor(project: Project) : this(project.allModules())

    init {
        allModulesByExternalPath = modules
            .filter { it.externalProjectPath != null && it.externalProjectId != null }
            .groupBy { it.externalProjectPath!! }

        baseModuleByExternalPath = allModulesByExternalPath
            .mapValues { (_, modules) ->
                modules.reduce { m1, m2 ->
                    if (isSourceRootPrefix(m2.externalProjectId!!, m1.externalProjectId!!)) m2 else m1
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

fun Module.toModuleGroup() = ModuleSourceRootMap(project).toModuleGroup(this)
fun Module.getWholeModuleGroup() = ModuleSourceRootMap(project).getWholeModuleGroup(this)

private fun isSourceRootPrefix(externalId: String, previousModuleExternalId: String) =
    externalId.length < previousModuleExternalId.length && previousModuleExternalId.startsWith(externalId)

val Module.externalProjectId: String?
    get() = ExternalSystemApiUtil.getExternalProjectId(this)

val Module.externalProjectPath: String?
    get() = ExternalSystemApiUtil.getExternalProjectPath(this)

fun ModuleSourceRootGroup.allModules(): Set<Module> {
    val result = LinkedHashSet<Module>()
    result.add(baseModule)
    result.addAll(sourceRootModules)
    return result
}

fun List<ModuleSourceRootGroup>.exclude(excludeModules: Collection<Module>): List<ModuleSourceRootGroup> {
    return mapNotNull {
        if (it.baseModule in excludeModules)
            null
        else {
            val remainingSourceRootModules = it.sourceRootModules - excludeModules
            if (remainingSourceRootModules.isEmpty())
                null
            else
                ModuleSourceRootGroup(it.baseModule, remainingSourceRootModules)
        }
    }
}