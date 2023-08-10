// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.parcelize

import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.parcelize.ParcelizeAnnotationChecker
import org.jetbrains.kotlin.parcelize.ParcelizeDeclarationChecker
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm

class IdeParcelizeDeclarationCheckerComponentContainerContributor : StorageComponentContainerContributor {
    override fun registerModuleComponents(
        container: StorageComponentContainer,
        platform: TargetPlatform,
        moduleDescriptor: ModuleDescriptor,
    ) {
        if (platform.isJvm()) {
            val moduleInfo = moduleDescriptor.moduleInfo
            if (moduleInfo is ModuleSourceInfo && ParcelizeAvailability.isAvailable(moduleInfo.module)) {
                container.useInstance(ParcelizeDeclarationChecker())
                container.useInstance(ParcelizeAnnotationChecker())
            }
        }
    }
}