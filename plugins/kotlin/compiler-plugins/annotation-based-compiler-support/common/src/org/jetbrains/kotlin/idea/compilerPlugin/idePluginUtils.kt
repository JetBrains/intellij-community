// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin

import com.intellij.openapi.module.Module

fun Module.getSpecialAnnotations(prefix: String): List<String> {
    val kotlinFacet = org.jetbrains.kotlin.idea.facet.KotlinFacet.get(this) ?: return emptyList()
    val commonArgs = kotlinFacet.configuration.settings.compilerArguments ?: return emptyList()

    return commonArgs.pluginOptions
        ?.filter { it.startsWith(prefix) }
        ?.map { it.substring(prefix.length) }
        ?: emptyList()
}