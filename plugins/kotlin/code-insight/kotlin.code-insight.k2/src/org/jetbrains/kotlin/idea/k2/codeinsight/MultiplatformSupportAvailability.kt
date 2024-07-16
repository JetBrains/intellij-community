// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightBundle
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.isJvm

internal class MultiplatformSupportAvailability : AbstractMultiplatformSupportAvailability() {

    @Nls
    override fun name(): String = KotlinCodeInsightBundle.message("kmp.support.availability.name")

    override fun isEnabledByRegistry(): Boolean = Registry.`is`("kotlin.k2.kmp.enabled", true)

    override fun isSupportedPlatform(platform: TargetPlatform): Boolean = platform.isJvm() || platform.isCommon()

}