// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.gradleJava

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.ParcelizeAvailabilityProvider
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.util.GradleConstants

private class GradleParcelizeAvailabilityProvider : ParcelizeAvailabilityProvider {
    override fun isAvailable(module: Module): Boolean {
        val path = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return false
        val externalProjectInfo = ExternalSystemUtil.getExternalProjectInfo(module.project, GradleConstants.SYSTEM_ID, path) ?: return false
        val moduleData = GradleProjectResolverUtil.findModule(externalProjectInfo.externalProjectStructure, path) ?: return false
        return ExternalSystemApiUtil.find(moduleData, ParcelizeIdeModel.KEY)?.data?.isEnabled ?: false
    }
}