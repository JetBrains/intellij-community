// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.testSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKtModule

/**
 * Returns the [KtModule]s for which modification events need to be published when this [Module] is affected.
 *
 * An event should be published for the production source info first, so this function cannot use `sourceModuleInfos` as is. Ordering the
 * production source module first allows some subscribers like session invalidation to *also* invalidate the test source module right away
 * via friend dependencies. Then the invalidation service can quickly skip invalidation of the test source module on the second event, as it
 * will already be invalidated.
 */
fun Module.toKtModulesForModificationEvents(): List<KtModule> =
    listOfNotNull(productionSourceInfo?.toKtModule(), testSourceInfo?.toKtModule())

/**
 * Publishes an out-of-block modification event for this [KtModule]. Must be called in a write action.
 */
fun KtModule.publishModuleOutOfBlockModification() {
    ThreadingAssertions.assertWriteAccess()

    project.analysisMessageBus.syncPublisher(KotlinTopics.MODULE_OUT_OF_BLOCK_MODIFICATION).onModification(this)
}

/**
 * Publishes an out-of-block modification event for this [Module]'s production and test source [KtModule]s. Must be called in a write
 * action.
 */
fun Module.publishModuleOutOfBlockModification() {
    ThreadingAssertions.assertWriteAccess()

    toKtModulesForModificationEvents().forEach { it.publishModuleOutOfBlockModification() }
}

/**
 * Publishes a global source out-of-block modification event in the given [Project]. Must be called in a write action.
 */
fun Project.publishGlobalSourceOutOfBlockModification() {
    ThreadingAssertions.assertWriteAccess()

    analysisMessageBus.syncPublisher(KotlinTopics.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION).onModification()
}
