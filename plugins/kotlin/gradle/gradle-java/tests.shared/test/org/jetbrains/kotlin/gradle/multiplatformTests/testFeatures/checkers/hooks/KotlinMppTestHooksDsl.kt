// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.hooks

import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContext
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.TestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.writeAccess

/**
 * Allows 'hooking' into any stage of the import test, executing custom logic/checks/tests at this stage.
 */
interface KotlinMppTestHooksDsl {
    fun TestConfigurationDslScope.runBeforeImport(action: KotlinMppTestsContext.() -> Unit) {
        configuration.beforeImportHooks += action
    }

    fun TestConfigurationDslScope.runAfterImport(action: KotlinMppTestsContext.() -> Unit) {
        configuration.afterImportHooks += action
    }

    fun TestConfigurationDslScope.runBeforeTestExecution(action: KotlinMppTestsContext.() -> Unit) {
        configuration.beforeTestExecutionHooks += action
    }

    fun TestConfigurationDslScope.runAfterTestExecution(action: KotlinMppTestsContext.() -> Unit) {
        configuration.afterTestExecutionHooks += action
    }
}

object KotlinMppTestHooks : TestFeature<KotlinMppTestHooksConfiguration> {
    override fun createDefaultConfiguration(): KotlinMppTestHooksConfiguration {
        return KotlinMppTestHooksConfiguration()
    }

    override fun KotlinMppTestsContext.beforeImport() {
        testConfiguration.configuration.beforeImportHooks.forEach { it() }
    }

    override fun KotlinMppTestsContext.afterImport() {
        testConfiguration.configuration.afterImportHooks.forEach { it() }

    }

    override fun KotlinMppTestsContext.beforeTestExecution() {
        testConfiguration.configuration.beforeTestExecutionHooks.forEach { it() }
    }

    override fun KotlinMppTestsContext.afterTestExecution() {
        testConfiguration.configuration.afterTestExecutionHooks.forEach { it() }
    }
}

class KotlinMppTestHooksConfiguration {
    val beforeImportHooks = mutableListOf<KotlinMppTestsContext.() -> Unit>()
    val afterImportHooks = mutableListOf<KotlinMppTestsContext.() -> Unit>()
    val beforeTestExecutionHooks = mutableListOf<KotlinMppTestsContext.() -> Unit>()
    val afterTestExecutionHooks = mutableListOf<KotlinMppTestsContext.() -> Unit>()
}

private val TestConfigurationDslScope.configuration
    get() = writeAccess.getConfiguration(KotlinMppTestHooks)
