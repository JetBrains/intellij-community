// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.facet

import org.jdom.Element
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.deserializeFacetSettings
import org.jetbrains.kotlin.config.serializeFacetSettings

class KotlinFacetConfigurationImpl : KotlinFacetConfiguration {
    override var settings = KotlinFacetSettings()
        private set

    @Suppress("OVERRIDE_DEPRECATION")
    override fun readExternal(element: Element) {
        settings = deserializeFacetSettings(element).also { it.updateMergedArguments() }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun writeExternal(element: Element) {
        settings.serializeFacetSettings(element)
    }
}
