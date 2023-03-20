// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.execution.Location
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.run.forceGradleRunnerInMPP
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm

class KotlinMultiplatformJvmTestClassGradleConfigurationProducer : AbstractKotlinMultiplatformTestClassGradleConfigurationProducer() {
    override val forceGradleRunner get() = forceGradleRunnerInMPP()
    override fun isApplicable(module: Module, platform: TargetPlatform) = platform.isJvm()

    override fun getPsiClassForLocation(contextLocation: Location<*>) = getTestClassForJvm(contextLocation)
}