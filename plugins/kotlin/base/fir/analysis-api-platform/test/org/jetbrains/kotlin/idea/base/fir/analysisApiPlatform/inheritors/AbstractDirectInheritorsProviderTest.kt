// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.inheritors

import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDirectInheritorsProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinResolutionScopeEnlarger
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinResolutionScopeProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import java.io.File

abstract class AbstractDirectInheritorsProviderTest : AbstractInheritorsProviderTest() {
    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base").resolve("fir").resolve("analysis-api-platform").resolve("testData").resolve("directInheritors")

    override fun resolveInheritors(targetClass: KtClass, useSiteModule: KaModule): List<ClassId> =
        KotlinDirectInheritorsProvider.getInstance(project)
            .getDirectKotlinInheritors(targetClass, KotlinResolutionScopeEnlarger.getEnlargedResolutionScope(useSiteModule))
            .mapNotNull { it.classIdIfNonLocal }
}
