// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure

import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.util.Frontend10ApiUsage

/**
 * The list of [KtModule]s which have this [KtModule] in its dependsOn (aka refinement) dependencies.
 * For instance, for a [KtModule] corresponding to a 'commonMain' KMP source set, the list will include all modules
 * from the same source set tree (production source sets), because 'commonMain' is the root source set.
 */
@OptIn(Frontend10ApiUsage::class)
fun KtModule.getDependentDependsOnKtModules(): List<KtModule> {
    val moduleInfo = this.moduleInfo as? ModuleSourceInfo ?: return emptyList()
    val implementingModules = moduleInfo.module.implementingModules
    return implementingModules.mapNotNull { it.productionOrTestSourceModuleInfo?.toKtModule() }
}
