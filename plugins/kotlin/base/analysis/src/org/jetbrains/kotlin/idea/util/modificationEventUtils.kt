// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.module.Module
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.platform.modification.publishModuleOutOfBlockModificationEvent
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProduction
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForTest

/**
 * Returns the [KaModule]s for which modification events need to be published when this [Module] is affected.
 *
 * An event should be published for the production source info first, so this function cannot use `sourceModuleInfos` as is. Ordering the
 * production source module first allows some subscribers like session invalidation to *also* invalidate the test source module right away
 * via friend dependencies. Then the invalidation service can quickly skip invalidation of the test source module on the second event, as it
 * will already be invalidated.
 */
fun Module.toKaModulesForModificationEvents(): List<KaModule> =
    listOfNotNull(toKaSourceModuleForProduction(), toKaSourceModuleForTest())

/**
 * Publishes an out-of-block modification event for this [Module]'s production and test source [KaModule]s. Must be called in a write
 * action.
 */
@OptIn(KaPlatformInterface::class)
fun Module.publishModuleOutOfBlockModificationEvent() {
    ThreadingAssertions.assertWriteAccess()

    toKaModulesForModificationEvents().forEach { it.publishModuleOutOfBlockModificationEvent() }
}
