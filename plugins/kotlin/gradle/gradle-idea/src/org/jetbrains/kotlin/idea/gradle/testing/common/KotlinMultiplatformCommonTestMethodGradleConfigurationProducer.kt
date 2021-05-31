// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.testing.common

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.run.AbstractKotlinMultiplatformTestMethodGradleConfigurationProducer
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon

class KotlinMultiplatformCommonTestMethodGradleConfigurationProducer : AbstractKotlinMultiplatformTestMethodGradleConfigurationProducer() {
    override fun isApplicable(module: Module, platform: TargetPlatform) = platform.isCommon()
}