/*
 * Copyright (C) 2023 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
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
package com.intellij.compose.ide.plugin.k2.intentions

import com.intellij.compose.ide.plugin.shared.intentions.ComposableAnnotationQuickFixTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

/**
 * Tests for adding `@Composable` annotation quick fix
 *
 * Based on: [com.android.tools.compose.intentions.AddComposableAnnotationQuickFixTest]
 */
class K2AddComposableAnnotationQuickFixTest : ComposableAnnotationQuickFixTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}
