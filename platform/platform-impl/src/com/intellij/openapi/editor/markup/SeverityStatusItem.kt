// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

import com.intellij.lang.annotation.HighlightSeverity
import javax.swing.Icon

/** The data necessary for creating severity-based [StatusItem]s */
data class SeverityStatusItem(val severity: HighlightSeverity, val icon: Icon, val problemCount: Int, val countMessage: String)