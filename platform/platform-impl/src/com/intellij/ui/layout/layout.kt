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

import com.intellij.ui.components.Panel
import com.intellij.ui.layout.LCFlags.*
import javax.swing.JPanel

/**
 * Claims all available space in the container for the columns ([LCFlags.fillX]).
 * At least one component need to have a [CCFlags.grow] constraint for it to fill the container.
 *
 * See [docs](https://github.com/JetBrains/intellij-community/tree/master/platform/platform-impl/src/com/intellij/ui/layout)
 *
 * Check `Tools -> Internal Actions -> UI -> MigLayout Debug Mode` to turn on debug painting.
 *
 * JTextComponent component automatically has [CCFlags.growX].
 * ToolbarDecorator component automatically has [CCFlags.grow] and [CCFlags.push].
 */
inline fun panel(vararg constraints: LCFlags, title: String? = null, init: LayoutBuilder.() -> Unit): JPanel {
  val builder = createLayoutBuilder()
  builder.init()

  val panel = Panel(title, layout = null)
  builder.`$`.build(panel, constraints)
  return panel
}

inline fun verticalPanel(init: Row.() -> Unit) = panel(noGrid, flowY, fillX) {
  row(init = init)
}