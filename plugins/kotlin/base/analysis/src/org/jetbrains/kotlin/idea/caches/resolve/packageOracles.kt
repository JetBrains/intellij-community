// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.PackageOracle
import org.jetbrains.kotlin.analyzer.PackageOracleFactory
import org.jetbrains.kotlin.idea.caches.PerModulePackageCacheService
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleOrigin
import org.jetbrains.kotlin.idea.caches.project.projectSourceModules
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.resolve.jvm.KotlinJavaPsiFacade
import org.jetbrains.kotlin.platform.jvm.isJvm

class IdePackageOracleFactory(val project: Project) : PackageOracleFactory {
    override fun createOracle(moduleInfo: ModuleInfo): PackageOracle {
        if (moduleInfo !is IdeaModuleInfo) return PackageOracle.Optimistic

        return when {
            moduleInfo.platform.isJvm() -> when (moduleInfo.moduleOrigin) {
                ModuleOrigin.LIBRARY -> JavaPackagesOracle(moduleInfo, project)
                ModuleOrigin.MODULE -> JvmSourceOracle(moduleInfo, project)
                ModuleOrigin.OTHER -> PackageOracle.Optimistic
            }
            else -> when (moduleInfo.moduleOrigin) {
                ModuleOrigin.MODULE -> KotlinSourceFilesOracle(moduleInfo, project)
                else -> PackageOracle.Optimistic // binaries for non-jvm platform need some oracles based on their structure
            }
        }
    }

    private class JavaPackagesOracle(moduleInfo: IdeaModuleInfo, project: Project) : PackageOracle {
        private val scope = moduleInfo.contentScope
        private val facade: KotlinJavaPsiFacade = project.service()

        override fun packageExists(fqName: FqName) = facade.findPackage(fqName.asString(), scope) != null
    }

    private class KotlinSourceFilesOracle(moduleInfo: IdeaModuleInfo, private val project: Project) : PackageOracle {
        private val cacheService: PerModulePackageCacheService = project.service()
        private val sourceModules = moduleInfo.projectSourceModules()

        override fun packageExists(fqName: FqName): Boolean {
            return sourceModules.any { cacheService.packageExists(fqName, it) }
        }
    }

    private class JvmSourceOracle(moduleInfo: IdeaModuleInfo, project: Project) : PackageOracle {
        private val javaPackagesOracle = JavaPackagesOracle(moduleInfo, project)
        private val kotlinSourceOracle = KotlinSourceFilesOracle(moduleInfo, project)

        override fun packageExists(fqName: FqName) =
            javaPackagesOracle.packageExists(fqName)
                    || kotlinSourceOracle.packageExists(fqName)
                    || fqName.isSubpackageOf(ANDROID_SYNTHETIC_PACKAGE_PREFIX)
    }
}

private val ANDROID_SYNTHETIC_PACKAGE_PREFIX = FqName("kotlinx.android.synthetic")