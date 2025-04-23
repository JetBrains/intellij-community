// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.run

import com.intellij.execution.Location
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.platform.jvm.isJvm

class KotlinJvmTestMethodGradleConfigurationProducer : AbstractKotlinTestMethodGradleConfigurationProducer() {
    override val forceGradleRunner: Boolean get() = false
    override val hasTestFramework: Boolean get() = canRunJvmTests()
    override fun isApplicable(module: Module): Boolean = module.platform.isJvm() && !module.isNewMultiPlatformModule

    override fun getPsiMethodForLocation(contextLocation: Location<*>): PsiMethod? = getTestMethodForJvm(contextLocation)
}