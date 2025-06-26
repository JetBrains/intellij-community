// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.hooks

import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinSyncTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess

/**
 * Allows 'hooking' into any stage of the import test, executing custom logic/checks/tests at this stage.
 */
interface KotlinMppTestHooksDsl {
    fun TestConfigurationDslScope.runBeforeImport(action: KotlinSyncTestsContext.() -> Unit) {
        configuration.beforeImportHooks += action
    }

    fun TestConfigurationDslScope.runAfterImport(action: KotlinSyncTestsContext.() -> Unit) {
        configuration.afterImportHooks += action
    }

    fun TestConfigurationDslScope.runBeforeTestExecution(action: KotlinSyncTestsContext.() -> Unit) {
        configuration.beforeTestExecutionHooks += action
    }

    fun TestConfigurationDslScope.runAfterTestExecution(action: KotlinSyncTestsContext.() -> Unit) {
        configuration.afterTestExecutionHooks += action
    }
}

object KotlinMppTestHooks : TestFeature<KotlinMppTestHooksConfiguration> {
    override fun createDefaultConfiguration(): KotlinMppTestHooksConfiguration {
        return KotlinMppTestHooksConfiguration()
    }

    override fun KotlinSyncTestsContext.beforeImport() {
        testConfiguration.configuration.beforeImportHooks.forEach { it() }
    }

    override fun KotlinSyncTestsContext.afterImport() {
        testConfiguration.configuration.afterImportHooks.forEach { it() }

    }

    override fun KotlinSyncTestsContext.beforeTestExecution() {
        testConfiguration.configuration.beforeTestExecutionHooks.forEach { it() }
    }

    override fun KotlinSyncTestsContext.afterTestExecution() {
        testConfiguration.configuration.afterTestExecutionHooks.forEach { it() }
    }
}

class KotlinMppTestHooksConfiguration {
    val beforeImportHooks = mutableListOf<KotlinSyncTestsContext.() -> Unit>()
    val afterImportHooks = mutableListOf<KotlinSyncTestsContext.() -> Unit>()
    val beforeTestExecutionHooks = mutableListOf<KotlinSyncTestsContext.() -> Unit>()
    val afterTestExecutionHooks = mutableListOf<KotlinSyncTestsContext.() -> Unit>()
}

private val TestConfigurationDslScope.configuration
    get() = writeAccess.getConfiguration(KotlinMppTestHooks)
