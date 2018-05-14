// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import com.intellij.ui.layout.migLayout.*
import java.awt.Container
import javax.swing.ButtonGroup
import javax.swing.JLabel

@PublishedApi
@JvmOverloads
internal fun createLayoutBuilder(isUseMagic: Boolean = true): LayoutBuilder {
  return LayoutBuilder(MigLayoutBuilder(createIntelliJSpacingConfiguration(), isUseMagic = isUseMagic))
}

interface LayoutBuilderImpl {
  fun newRow(label: JLabel? = null, buttonGroup: ButtonGroup? = null, separated: Boolean = false): Row

  // backward compatibility
  @Deprecated(level = DeprecationLevel.HIDDEN, message = "deprecated")
  fun newRow(label: JLabel? = null, buttonGroup: ButtonGroup? = null, separated: Boolean = false, indented: Boolean = false) = newRow(label, buttonGroup, separated)

  fun build(container: Container, layoutConstraints: Array<out LCFlags>)

  fun noteRow(text: String, linkHandler: ((url: String) -> Unit)? = null)
}