// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.low.level.api.ide

import org.jetbrains.kotlin.analysis.low.level.api.fir.api.services.FirSealedClassInheritorsProcessorFactory
import org.jetbrains.kotlin.fir.declarations.SealedClassInheritorsProvider

internal class FirSealedClassInheritorsProcessorFactoryIdeImpl : FirSealedClassInheritorsProcessorFactory() {
    override fun createSealedClassInheritorsProvider(): SealedClassInheritorsProvider {
        return SealedClassInheritorsProviderIdeImpl()
    }
}