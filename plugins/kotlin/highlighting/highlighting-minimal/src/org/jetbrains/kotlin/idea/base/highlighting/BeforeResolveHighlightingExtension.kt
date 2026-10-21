// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.highlighter.visitor.AbstractHighlightingVisitor

@ApiStatus.Internal
interface BeforeResolveHighlightingExtension {
    fun createVisitor(holder: HighlightInfoHolder): AbstractHighlightingVisitor

    companion object {
        val EP_NAME: ExtensionPointName<BeforeResolveHighlightingExtension> =
            ExtensionPointName.create("org.jetbrains.kotlin.beforeResolveHighlightingVisitor")
    }
}