/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.layout

import java.awt.Container
import javax.swing.ButtonGroup
import javax.swing.JLabel

// see com.intellij.uiDesigner.core.AbstractLayout.DEFAULT_HGAP and DEFAULT_VGAP
// https://docs.google.com/document/d/1DKnLkO-7_onA7_NCw669aeMH5ltNvw-QMiQHnXu8k_Y/edit

internal const val HORIZONTAL_GAP = 10
internal const val VERTICAL_GAP = 5

fun createLayoutBuilder() = LayoutBuilder(MigLayoutBuilder())

// cannot use the same approach as in case of Row because cannot access to `build` method in inlined `panel` method,
// in any case Kotlin compiler does the same thing —
// "When a protected member is accessed from an inline function, a public accessor method is created to provide an access to that protected member from the outside of the class where the function will be inlined to."
// (https://youtrack.jetbrains.com/issue/KT-12215)
interface LayoutBuilderImpl {
  fun newRow(label: JLabel? = null, buttonGroup: ButtonGroup? = null): Row

  fun build(container: Container, layoutConstraints: Array<out LCFlags>)

  fun noteRow(text: String)
}