// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Status item to be displayed in the top-right corner of the editor,
 * containing a text (not necessarily a number), possible icon and details text for popup
 */
data class StatusItem @JvmOverloads constructor(@Nls @get:Nls val text: String, val icon: Icon? = null, val detailsText: String? = null)