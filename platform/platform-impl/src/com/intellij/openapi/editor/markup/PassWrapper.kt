// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

import org.jetbrains.annotations.Nls

/**
 * Light wrapper for <code>ProgressableTextEditorHighlightingPass</code> with only essential UI data.
 */
data class PassWrapper(@Nls @get:Nls val presentableName: String, val percent: Int)