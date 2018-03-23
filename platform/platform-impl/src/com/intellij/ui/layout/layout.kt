// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.ui.components.Panel
import javax.swing.JPanel

/**
 * Claims all available space in the container for the columns ([LCFlags.fillX], if `constraints` is passed, `fillX` will be not applied - add it explicitly if need).
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
  builder.builder.build(panel, constraints)
  return panel
}