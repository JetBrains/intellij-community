// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.testFramework.SkipSlowTestLocally
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode


@SkipSlowTestLocally
class K1KotlinCompilerRefHelperTest : AbstractKotlinCompilerRefHelperTest() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1


    override fun setUp() {
        super.setUp()
        project.enableK1Compiler()
    }
}
