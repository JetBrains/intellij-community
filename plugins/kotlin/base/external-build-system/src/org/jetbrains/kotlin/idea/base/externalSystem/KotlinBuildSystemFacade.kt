// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.externalSystem

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.IntellijInternalApi

/**
 * Intended to provide build system specific information about a given project, which is not relevant
 * during regular 'import' and therefore is not present in the project structure.
 *
 * For example, The Gradle implementation might reach into external system 'DataNodes'
 */
@IntellijInternalApi
interface KotlinBuildSystemFacade {
    fun findSourceSet(module: Module): KotlinBuildSystemSourceSet?

    companion object {

        @JvmStatic
        fun getInstance(): KotlinBuildSystemFacade = KotlinBuildSystemCompositeFacade(EP_NAME.extensionList)

        val EP_NAME = ExtensionPointName.create<KotlinBuildSystemFacade>(
            "org.jetbrains.kotlin.idea.base.externalSystem.kotlinBuildSystemFacade"
        )
    }
}

private class KotlinBuildSystemCompositeFacade(
    private val instances: List<KotlinBuildSystemFacade>
) : KotlinBuildSystemFacade {
    override fun findSourceSet(module: Module): KotlinBuildSystemSourceSet? {
        return instances.firstNotNullOfOrNull { instance -> instance.findSourceSet(module) }
    }
}