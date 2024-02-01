// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.analysis.providers.modificationEvents

import org.jetbrains.kotlin.analysis.providers.topics.KotlinModificationEventKind
import org.jetbrains.kotlin.analysis.providers.topics.isGlobalLevel

abstract class AbstractKotlinGlobalModificationEventTest : AbstractKotlinModificationEventTest() {
    override fun setUp() {
        super.setUp()

        require(expectedEventKind.isGlobalLevel)
    }

    protected fun createTracker(additionalAllowedEventKinds: Set<KotlinModificationEventKind> = emptySet()): ModificationEventTracker =
        createGlobalTracker(additionalAllowedEventKinds)

    protected fun createTracker(additionalAllowedEventKind: KotlinModificationEventKind): ModificationEventTracker =
        createGlobalTracker(setOf(additionalAllowedEventKind))
}
