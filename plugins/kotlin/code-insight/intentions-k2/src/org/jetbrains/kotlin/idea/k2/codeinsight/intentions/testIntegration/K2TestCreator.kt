// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions.testIntegration

import org.jetbrains.kotlin.idea.codeinsights.impl.base.testIntegration.AbstractKotlinTestCreator
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class K2TestCreator: AbstractKotlinTestCreator() {
    override fun createTestIntention(): SelfTargetingRangeIntention<KtNamedDeclaration> =
        KotlinCreateTestIntention()
}