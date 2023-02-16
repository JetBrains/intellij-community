// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests.testFeatures

import org.jetbrains.kotlin.gradle.newTests.*
import org.jetbrains.kotlin.idea.codeInsight.gradle.BuildGradleModelDebuggerOptions
import org.jetbrains.kotlin.idea.codeInsight.gradle.BuiltGradleModel
import org.jetbrains.kotlin.idea.codeInsight.gradle.map
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBinary
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import kotlin.reflect.KClass

object CustomChecksFeature : AbstractTestChecker<CustomCheck>() {
    override fun createDefaultConfiguration() = CustomCheck()

    override fun KotlinMppTestsContext.check(additionalTestClassifier: String?) {
        testConfiguration.getConfiguration(CustomChecksFeature).check(this)
    }
}

data class CustomCheck(var check: KotlinMppTestsContext.() -> Unit = { })

interface CustomChecksDsl {
    /**
     * Allows to have custom post-import checks via `customChecks { ... }`-block
     *
     * Only perform checks there, no need to setup or teardown parts of the infrastructure
     * that were not introduced by your own block (e.g. no need to call `configureByFiles` or `importProject`)
     */
    fun TestConfigurationDslScope.customChecks(check: KotlinMppTestsContext.() -> Unit) {
        writeAccess.getConfiguration(CustomChecksFeature).check = check
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
