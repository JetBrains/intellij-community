// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.facet

import com.intellij.facet.FacetConfiguration
import org.jetbrains.kotlin.config.KotlinFacetSettings

interface KotlinFacetConfiguration : FacetConfiguration {
    val settings: KotlinFacetSettings
}