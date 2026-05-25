// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.kotlin.idea.facet.KotlinFacet

fun KotlinGradleImportingTestCase.facetSettings(moduleName: String): IKotlinFacetSettings {
    val facet = KotlinFacet.get(getModule(moduleName)) ?: error("Kotlin facet not found in module $moduleName")
    return facet.configuration.settings
}

val KotlinGradleImportingTestCase.facetSettings: IKotlinFacetSettings
    get() = facetSettings("project.main")