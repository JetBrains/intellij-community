// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.caches.resolve

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.resolve.diagnostics.KotlinSuppressCache
import org.jetbrains.kotlin.idea.util.application.getServiceSafe

interface KotlinCacheService {
    companion object {
        fun getInstance(project: Project): KotlinCacheService = project.getServiceSafe()
    }

    /**
     * Provides resolution facade for [elements], guaranteeing that the resolution will be seen from the [platform]-perspective.
     *
     * This allows to get resolution for common sources in MPP from the perspective of given platform (with expects substituted to actuals,
     * declarations resolved from platform-specific artifacts, ModuleDescriptors will contain only platform dependencies, etc.)
     *
     * It is equivalent to usual [getResolutionFacade]-overloads if platform(s) of module(s) containing [elements] are equal to [platform]
     *
     * Doesn't support scripts or any other 'special' files.
     */
    fun getResolutionFacadeWithForcedPlatform(elements: List<KtElement>, platform: TargetPlatform): ResolutionFacade

    fun getResolutionFacade(element: KtElement): ResolutionFacade
    fun getResolutionFacade(elements: List<KtElement>): ResolutionFacade
    fun getResolutionFacadeByFile(file: PsiFile, platform: TargetPlatform): ResolutionFacade?

    fun getSuppressionCache(): KotlinSuppressCache
    fun getResolutionFacadeByModuleInfo(moduleInfo: ModuleInfo, platform: TargetPlatform): ResolutionFacade?

    fun getResolutionFacadeByModuleInfo(moduleInfo: ModuleInfo, settings: PlatformAnalysisSettings): ResolutionFacade?
}