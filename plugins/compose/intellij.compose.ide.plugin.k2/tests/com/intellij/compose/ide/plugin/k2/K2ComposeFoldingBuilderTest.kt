/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.intellij.compose.ide.plugin.k2

import com.intellij.compose.ide.plugin.shared.ComposeFoldingBuilderTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

/** Test for [K2ComposeFoldingBuilder]. */
internal class K2ComposeFoldingBuilderTest : ComposeFoldingBuilderTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}
