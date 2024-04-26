// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures

import org.jetbrains.kotlin.gradle.multiplatformTests.*
import org.jetbrains.kotlin.idea.codeInsight.gradle.BuildGradleModelDebuggerOptions
import org.jetbrains.kotlin.idea.codeInsight.gradle.BuiltGradleModel
import org.jetbrains.kotlin.idea.codeInsight.gradle.map
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBinary
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import kotlin.reflect.KClass

/**
 * Allows to have custom post-import checks via `customChecks { ... }`-block
 *
 * Test infra will always load the project into IDE (a.k.a. `configureByFiles`)
 *
 * Use `doTest(runImport = false)` if you don't need the import to be run before your custom checks
 */
interface CustomChecksDsl {
    fun TestConfigurationDslScope.customChecks(check: KotlinMppTestsContext.() -> Unit) {
        writeAccess.getConfiguration(CustomImportChecker).check = check
    }

    fun KotlinMppTestsContext.buildKotlinMPPGradleModel(
        debuggerOptions: BuildGradleModelDebuggerOptions? = null
    ): BuiltGradleModel<KotlinMPPGradleModel> = buildGradleModel(KotlinMPPGradleModelBinary::class, debuggerOptions)
        .map { model -> ObjectInputStream(ByteArrayInputStream(model.data)).readObject() as KotlinMPPGradleModel }

    fun <T : Any> KotlinMppTestsContext.buildGradleModel(
        clazz: KClass<T>,
        debuggerOptions: BuildGradleModelDebuggerOptions? = null
    ): BuiltGradleModel<T> =
        org.jetbrains.kotlin.idea.codeInsight.gradle.buildGradleModel(
            this.testProjectRoot,
            gradleVersion,
            gradleJdkPath.absolutePath,
            clazz,
            debuggerOptions
        )
}

object CustomImportChecker : AbstractTestChecker<CustomCheck>() {
    override fun createDefaultConfiguration() = CustomCheck()

    override fun KotlinMppTestsContext.check() {
        testConfiguration.getConfiguration(CustomImportChecker).check(this)
    }
}

data class CustomCheck(var check: KotlinMppTestsContext.() -> Unit = { })
