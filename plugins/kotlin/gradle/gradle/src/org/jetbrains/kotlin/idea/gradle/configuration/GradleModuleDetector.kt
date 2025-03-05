// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.configuration

import com.intellij.facet.FacetManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.util.isGradleModule
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.BuildSystemTypeDetector
import java.nio.file.Path
import kotlin.io.path.Path

class GradleModuleDetector : BuildSystemTypeDetector {
    override fun detectBuildSystemType(module: Module): BuildSystemType? {
        if (module.isGradleModule) {
            // We need to detect AmperGradle here (in addition to AmperBuildSystemTypeDetector from the Amper plugin).
            // If we didn't, we would just return Gradle or AndroidGradle here when the AmperBuildSystemTypeDetector does not run first
            // (this would depend on the entrypoint order, and on whether the Amper plugin is enabled at all).
            if (module.isConfiguredViaAmperFiles()) {
                return BuildSystemType.AmperGradle
            }
            if (FacetManager.getInstance(module).allFacets.any { it.name == "Android" }) {
                return BuildSystemType.AndroidGradle
            }
            return BuildSystemType.Gradle
        }
        return null
    }
}

@ApiStatus.Internal
fun Module.isConfiguredViaAmperFiles(): Boolean {
    // We can't rely on the presence of the Amper plugin in settings.gradle.kts. Even if the Amper plugin is there, each 
    // subproject can either be pure Gradle or Gradle-based Amper depending on the presence of Amper module files.
    // That's why we have to check if the subproject itself has Amper files.
    
    // Not all modules represent Gradle projects, some synthetic modules are generated for Gradle source sets (e.g. my-module.commonMain).
    // This is why we need to get the actual Gradle project path before looking for Amper files.
    val gradleProjectVirtualDir = gradleProjectDir?.let { VfsUtil.findFile(it, false) }

    // It doesn't seem possible to detect Gradle-based Amper files without hardcoding the names nor depending on the Amper plugin.
    return gradleProjectVirtualDir?.findChild("module.yaml") != null || gradleProjectVirtualDir?.findChild("module.amper") != null
}

/**
 * Returns path to the directory of the Gradle project containing this [Module].
 * 
 * Not all modules represent Gradle projects, some synthetic modules are generated for Gradle source sets (e.g. my-module.commonMain).
 * This property is the actual Gradle project path even when called on a synthetic source set module.
 */
private val Module.gradleProjectDir: Path?
    get() = ExternalSystemApiUtil.getExternalProjectPath(this)?.let { Path(it) }
