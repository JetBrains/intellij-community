// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.lineMarkers.test

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.codeInsight.AbstractKotlinPsiBasedTestFrameworkTest

abstract class AbstractKotlinPsiBasedTestFrameworkK2Test: AbstractKotlinPsiBasedTestFrameworkTest() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
}
