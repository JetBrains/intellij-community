// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.inheritors

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import java.io.File

abstract class AbstractSealedInheritorsProviderTest : AbstractInheritorsProviderTest() {
    override fun getTestDataDirectory(): File =
        KotlinRoot.DIR.resolve("base").resolve("fir").resolve("analysis-api-platform").resolve("testData").resolve("sealedInheritors")

    override fun resolveInheritors(targetClass: KtClass, useSiteModule: KaModule): List<ClassId> {
        assertTrue("Expected the target type `${targetClass.classIdIfNonLocal}` to be sealed.", targetClass.isSealed())

        return analyze(useSiteModule) {
            val classSymbol = targetClass.namedClassSymbol
                ?: error("Expected the target class `${targetClass.classIdIfNonLocal}` to have a named class or object symbol.")

            classSymbol.sealedClassInheritors.map { it.classId ?: error("Sealed class inheritors should not be local.") }
        }
    }
}
