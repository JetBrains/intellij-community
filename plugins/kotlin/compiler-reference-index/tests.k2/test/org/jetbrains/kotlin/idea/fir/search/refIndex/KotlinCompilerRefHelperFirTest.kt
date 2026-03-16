// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.search.refIndex

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.search.refIndex.AbstractKotlinCompilerRefHelperTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil

class KotlinCompilerRefHelperFirTest : AbstractKotlinCompilerRefHelperTest() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun setUp() {
        super.setUp()
        ConfigLibraryUtil.configureKotlinRuntime(module)
    }
}