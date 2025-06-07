// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.propertyBased

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.propertyBased.KotlinCodeInsightSanityTest
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

class FirKotlinCodeInsightSanity: KotlinCodeInsightSanityTest() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }
}
