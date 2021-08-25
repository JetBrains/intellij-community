// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.facet.FacetManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module

val GRADLE_SYSTEM_ID = ProjectSystemId("GRADLE")

class GradleDetector : BuildSystemTypeDetector {
    override fun detectBuildSystemType(module: Module): BuildSystemType? {
        if (module.isGradleModule()) {
            if (FacetManager.getInstance(module).allFacets.any { it.name == "Android" }) {
                return BuildSystemType.AndroidGradle
            }
            return BuildSystemType.Gradle
        }
        return null
    }
}

fun Module.isGradleModule(): Boolean {
    return ExternalSystemApiUtil.isExternalSystemAwareModule(GRADLE_SYSTEM_ID, this)
}
