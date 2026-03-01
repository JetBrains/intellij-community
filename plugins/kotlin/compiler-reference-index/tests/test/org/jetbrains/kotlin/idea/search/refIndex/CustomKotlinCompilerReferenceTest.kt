// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.testFramework.SkipSlowTestLocally
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.highlighter.markers.OVERRIDDEN_FUNCTION
import org.jetbrains.kotlin.idea.highlighter.markers.SUBCLASSED_CLASS

@SkipSlowTestLocally
class K1CustomKotlinCompilerReferenceTest6 : AbstractCustomKotlinCompilerReferenceTest6() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1

    override fun testTooltips() {
        if (!isCompatibleVersions) return
        doTestTooltips(
            SUBCLASSED_CLASS.tooltip, OVERRIDDEN_FUNCTION.tooltip)
    }
}

