// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.js

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.CompilerModuleExtension
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinGradleFacade
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.util.isGradleModule
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.platform.isJs

val Module.jsTestOutputFilePath: String?
    get() {
        KotlinFacet.get(this)?.configuration?.settings?.testOutputPath?.let { return it }

        if (!shouldUseJpsOutput) return null

        val compilerExtension = CompilerModuleExtension.getInstance(this)
        val outputDir = compilerExtension?.compilerOutputUrlForTests ?: return null
        return JpsPathUtil.urlToPath("$outputDir/${name}_test.js")
    }

val Module.jsProductionOutputFilePath: String?
    get() {
        KotlinFacet.get(this)?.configuration?.settings?.productionOutputPath?.let { return it }

        if (!shouldUseJpsOutput) return null

        val compilerExtension = CompilerModuleExtension.getInstance(this)
        val outputDir = compilerExtension?.compilerOutputUrl ?: return null
        return JpsPathUtil.urlToPath("$outputDir/$name.js")
    }

fun Module.asJsModule(): Module? = takeIf { it.platform.isJs() }

val Module.shouldUseJpsOutput: Boolean
    get() {
        val gradleFacade = KotlinGradleFacade.instance ?: return false
        return !(isGradleModule && gradleFacade.isDelegatedBuildEnabled(this))
    }