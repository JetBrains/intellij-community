// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.testing.common

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.idea.gradleJava.run.AbstractKotlinMultiplatformTestMethodGradleConfigurationProducer
import org.jetbrains.kotlin.idea.gradleJava.extensions.KotlinMultiplatformCommonProducersProvider
import org.jetbrains.kotlin.idea.gradleJava.run.isProvidedByMultiplatformProducer
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.isJvm

class KotlinMultiplatformCommonTestMethodGradleConfigurationProducer : AbstractKotlinMultiplatformTestMethodGradleConfigurationProducer(),
                                                                       KotlinMultiplatformCommonProducersProvider {
    override fun isApplicable(module: Module, platform: TargetPlatform) = platform.isCommon() || platform.isJvm()
    override fun findExistingConfiguration(context: ConfigurationContext): RunnerAndConfigurationSettings? = null

    override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return !other.isProvidedByMultiplatformProducer() || super.isPreferredConfiguration(self, other)
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
        return !other.isProvidedByMultiplatformProducer() || super.shouldReplace(self, other)
    }

    override fun isProducedByCommonProducer(configuration: ConfigurationFromContext): Boolean {
        return configuration.isProducedBy(this.javaClass)
    }
}