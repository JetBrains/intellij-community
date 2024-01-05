// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.base.facet.JvmOnlyProjectChecker
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinSupportAvailability
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtFile

class MultiplatformSupportAvailability: KotlinSupportAvailability {
    override fun name(): String = KotlinBaseFirCodeInsight.message("kmp.support.availability.name")

    override fun isSupported(ktFile: KtFile): Boolean {
        if (Registry.`is`("kotlin.k2.kmp.enabled", true) || JvmOnlyProjectChecker.getInstance(ktFile.project).value()) return true
        val platform = ktFile.platform
        return platform.isJvm() || platform.isCommon()
    }
}