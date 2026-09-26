// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.test

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettings

fun configureCodeStyleAndRun(
    project: Project,
    configurator: (CodeStyleSettings) -> Unit = { },
    body: () -> Unit
) {
    val testSettings = CodeStyle.createTestSettings(CodeStyle.getSettings(project))
    CodeStyle.doWithTemporarySettings(project, testSettings, Runnable {
        configurator(testSettings)
        body()
    })
}
