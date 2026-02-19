// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

import com.intellij.openapi.util.NlsSafe

/*
 * Per language highlight level
 */
data class LanguageHighlightLevel(@NlsSafe @get:NlsSafe val langID: String, val level: InspectionsLevel)

