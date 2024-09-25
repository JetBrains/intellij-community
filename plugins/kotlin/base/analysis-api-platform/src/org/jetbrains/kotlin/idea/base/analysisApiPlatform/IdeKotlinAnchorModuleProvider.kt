// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinAnchorModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule

@ApiStatus.Internal
interface IdeKotlinAnchorModuleProvider: KotlinAnchorModuleProvider  {
   fun getAnchorLibraries(libraryModule: KaSourceModule): List<KaLibraryModule>
}