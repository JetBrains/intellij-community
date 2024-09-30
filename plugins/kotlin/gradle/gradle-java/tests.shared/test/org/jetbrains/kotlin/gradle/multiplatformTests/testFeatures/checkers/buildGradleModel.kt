// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers

import org.jetbrains.kotlin.gradle.multiplatformTests.KotlinMppTestsContext
import org.jetbrains.kotlin.idea.codeInsight.gradle.BuildGradleModelDebuggerOptions
import org.jetbrains.kotlin.idea.codeInsight.gradle.BuiltGradleModel
import org.jetbrains.kotlin.idea.codeInsight.gradle.map
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBinary
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import kotlin.reflect.KClass

fun <T : Any> KotlinMppTestsContext.buildGradleModel(
    clazz: KClass<T>,
    debuggerOptions: BuildGradleModelDebuggerOptions? = null
): BuiltGradleModel<T> =
    org.jetbrains.kotlin.idea.codeInsight.gradle.buildGradleModel(
        this.testProjectRoot, gradleVersion, gradleJdkPath.absolutePath, clazz, debuggerOptions
    )

fun KotlinMppTestsContext.buildKotlinMPPGradleModel(
    debuggerOptions: BuildGradleModelDebuggerOptions? = null
): BuiltGradleModel<KotlinMPPGradleModel> = buildGradleModel(KotlinMPPGradleModelBinary::class, debuggerOptions)
    .map { model -> ObjectInputStream(ByteArrayInputStream(model.data)).readObject() as KotlinMPPGradleModel }