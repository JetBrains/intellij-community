// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.module.Module
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.facet.stableName
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinResolveScopeEnlarger
import org.jetbrains.kotlin.name.Name

data class ModuleProductionSourceInfo internal constructor(
    override val module: Module
) : ModuleSourceInfoWithExpectedBy(forProduction = true) {

    override val name = Name.special("<production sources for module ${module.name}>")

    override val stableName: Name by lazy { module.stableName }

    override val contentScope: GlobalSearchScope
        get() = KotlinResolveScopeEnlarger.enlargeScope(module.moduleProductionSourceScope, module, isTestScope = false)
}