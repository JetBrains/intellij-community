// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.framework

import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.konan.isNative

/**
 * This StartupActivity creates KotlinSdk for projects containing non-jvm modules.
 * This activity is work-around required until the issue IDEA-203655 is fixed. The major case is to create
 * Kotlin SDK when the KotlinSourceRootType is created
 */
private class CreateKotlinSdkActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val modulesWithFacet = ProjectFacetManager.getInstance(project).getModulesWithFacet(KotlinFacetType.TYPE_ID)
        if (modulesWithFacet.isNotEmpty()) {
            KotlinSdkType.setUpIfNeeded {
                modulesWithFacet.any {
                    val platform = it.platform
                    platform.isJs() || platform.isNative() || platform.isCommon()
                }
            }
        }
    }
}