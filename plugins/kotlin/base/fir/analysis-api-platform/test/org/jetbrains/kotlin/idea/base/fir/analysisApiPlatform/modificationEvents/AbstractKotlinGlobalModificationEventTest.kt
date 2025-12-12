// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.modificationEvents

import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationEventKind
import org.jetbrains.kotlin.analysis.api.platform.modification.isGlobalLevel

abstract class AbstractKotlinGlobalModificationEventTest : AbstractKotlinModificationEventTest() {
    protected abstract val expectedEventKind: KotlinModificationEventKind

    override fun setUp() {
        super.setUp()

        require(expectedEventKind.isGlobalLevel)
    }

    protected fun createTracker(
        label: String,
        additionalAllowedEventKinds: Set<KotlinModificationEventKind> = emptySet(),
    ): ModificationEventTracker =
        createGlobalTracker(label, expectedEventKind, additionalAllowedEventKinds)

    protected fun createTracker(
        label: String,
        additionalAllowedEventKind: KotlinModificationEventKind,
    ): ModificationEventTracker =
        createGlobalTracker(label, expectedEventKind, setOf(additionalAllowedEventKind))
}
