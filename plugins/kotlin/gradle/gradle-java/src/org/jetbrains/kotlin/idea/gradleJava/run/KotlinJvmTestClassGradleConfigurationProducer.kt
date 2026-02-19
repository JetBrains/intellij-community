// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.execution.Location
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.platform.jvm.isJvm

class KotlinJvmTestClassGradleConfigurationProducer : AbstractKotlinTestClassGradleConfigurationProducer() {
    override val forceGradleRunner get() = false
    override val hasTestFramework get() = canRunJvmTests()
    override fun isApplicable(module: Module) = module.platform.isJvm() && !module.isNewMultiPlatformModule

    override fun getPsiClassForLocation(contextLocation: Location<*>) = getTestClassForJvm(contextLocation)
}