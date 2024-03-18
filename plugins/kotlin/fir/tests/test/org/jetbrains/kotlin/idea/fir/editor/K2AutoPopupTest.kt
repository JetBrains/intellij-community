// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.editor

import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.editor.KotlinAutoPopupTest
import org.jetbrains.kotlin.idea.fir.invalidateCaches

// TODO: rewrite this test to make it generated
internal class K2AutoPopupTest : KotlinAutoPopupTest() {
    override val pluginKind: KotlinPluginMode = KotlinPluginMode.K2

    override fun tearDown() {
        runAll(
            { runInEdtAndWait { project.invalidateCaches() } },
            { super.tearDown() }
        )
    }
}
