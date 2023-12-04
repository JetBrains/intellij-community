// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import com.intellij.ide.AboutPopupDescriptionProvider
import com.intellij.openapi.util.NlsContexts.DetailedDescription
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode

internal class KotlinAnalyzerVersionAboutPopupDescriptionProvider: AboutPopupDescriptionProvider {
    override fun getDescription(): @DetailedDescription String? = if (isApplicationInternalMode()) {
        KotlinBasePluginBundle.message("kotlin.analyzer.version.0", KotlinPluginLayout.ideCompilerVersion.rawVersion)
    } else null
}