// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.configuration.mpp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.caches.project.StableModuleNameProvider
import org.jetbrains.plugins.gradle.util.GradleConstants

private val LOG = Logger.getInstance(ExternalSystemBasedStableModuleNameProvider::class.java)

/**
 * Stable module name should match the Gradle name put into Kotlin facet during import.
 * The External system can provide the name, but only for modules that were imported from
 * a build system under the responsibility of the External system.
 * For all other modules their workspace name is used.
 * Some tests (e.g. [org.jetbrains.kotlin.idea.codeMetaInfo.AbstractCodeMetaInfoTest]) rely on this.
 * These tests set up modules and Kotlin facets manually without involvement of the External system.
 */
class ExternalSystemBasedStableModuleNameProvider(val project: Project) : StableModuleNameProvider {
    override fun getStableModuleName(module: Module): String =
        if (ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
            ExternalSystemModulePropertyManager.getInstance(module).getLinkedProjectId()
                ?: module.name.also { LOG.error("Don't have a LinkedProjectId for module $this for HMPP!") }
        } else module.name
}
