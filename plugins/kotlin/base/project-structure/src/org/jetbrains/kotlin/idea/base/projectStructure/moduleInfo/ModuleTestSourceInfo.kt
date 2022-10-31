// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.TestModuleProperties
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.SmartList
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.caches.project.cacheByClassInvalidatingOnRootModifications
import org.jetbrains.kotlin.idea.base.facet.stableName
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinBaseProjectStructureBundle
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinResolveScopeEnlarger
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.typeUtil.closure
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.idea.caches.resolve.ModuleTestSourceInfo as OldModuleTestSourceInfo

//TODO: (module refactoring) do not create ModuleTestSourceInfo when there are no test roots for module
data class ModuleTestSourceInfo internal constructor(
    override val module: Module
) : ModuleSourceInfoWithExpectedBy(forProduction = false), IdeaModuleInfo, OldModuleTestSourceInfo {
    override val name = Name.special("<test sources for module ${module.name}>")

    override val displayedName
        get() = KotlinBaseProjectStructureBundle.message("module.name.0.test", module.name)

    override val stableName: Name by lazy { module.stableName }

    override val contentScope: GlobalSearchScope
        get() = KotlinResolveScopeEnlarger.enlargeScope(module.moduleTestSourceScope, module, isTestScope = true)

    override fun modulesWhoseInternalsAreVisible(): Collection<ModuleInfo> =
        module.cacheByClassInvalidatingOnRootModifications(KeyForModulesWhoseInternalsAreVisible::class.java) {
            val list = SmartList<ModuleInfo>()

            list.addIfNotNull(module.productionSourceInfo)

            TestModuleProperties.getInstance(module).productionModule?.let {
                list.addIfNotNull(it.productionSourceInfo)
            }

            list.addAll(list.closure { it.expectedBy })

            list.toHashSet()
        }

    private object KeyForModulesWhoseInternalsAreVisible

    override fun keyForSdk() = KeyForSdks

    protected object KeyForSdks
}