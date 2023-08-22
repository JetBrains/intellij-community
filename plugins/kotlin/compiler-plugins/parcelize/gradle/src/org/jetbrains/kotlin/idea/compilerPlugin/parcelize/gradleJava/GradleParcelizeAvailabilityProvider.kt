// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.gradleJava

import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.ParcelizeAvailabilityProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleParcelizeAvailabilityProvider : ParcelizeAvailabilityProvider {
    override fun isAvailable(module: Module): Boolean {
        val path = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return false
        val externalProjectInfo = ExternalSystemUtil.getExternalProjectInfo(module.project, GradleConstants.SYSTEM_ID, path) ?: return false
        val moduleData = GradleProjectResolverUtil.findModule(externalProjectInfo.externalProjectStructure, path) ?: return false
        return ExternalSystemApiUtil.find(moduleData, ParcelizeIdeModel.KEY)?.data?.isEnabled ?: false
    }
}