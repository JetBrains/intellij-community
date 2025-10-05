/*
 * Copyright (C) 2019 The Android Open Source Project
 * Modified 2025 by JetBrains s.r.o.
 * Copyright (C) 2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.shared.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.annotations.ApiStatus


/**
 * Call text attribute name specified in the color schema.
 * @see [colorSchemes/IntelliJComposeColorSchemeDefault.xml]
 */
private const val COMPOSABLE_CALL_TEXT_COLOR_ATTRIBUTES_NAME = "IntelliJComposableCallTextAttributes"

/**
 * Defines a unique [TextAttributesKey] that is assigned for highlighting Composable function calls
 * in the editor. This key is used to associate specific text attributes with function calls annotated
 * as composable in projects using the Jetpack Compose framework.
 */
private val COMPOSABLE_CALL_TEXT_ATTRIBUTES_KEY: TextAttributesKey =
  TextAttributesKey.createTextAttributesKey(
    COMPOSABLE_CALL_TEXT_COLOR_ATTRIBUTES_NAME,
    DefaultLanguageHighlighterColors.FUNCTION_CALL,
  )

/**
 * Represents a custom highlight type used for marking Composable function calls within the Compose context.
 */
@ApiStatus.Internal
val COMPOSABLE_CALL_TEXT_TYPE: HighlightInfoType =
  HighlightInfoType.HighlightInfoTypeImpl(
    HighlightInfoType.SYMBOL_TYPE_SEVERITY,
    COMPOSABLE_CALL_TEXT_ATTRIBUTES_KEY,
  )
