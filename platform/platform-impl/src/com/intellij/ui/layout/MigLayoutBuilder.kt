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

import com.intellij.ui.components.noteComponent
import com.intellij.util.SmartList
import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout
import java.awt.Container
import javax.swing.JComponent
import javax.swing.text.JTextComponent

/**
 * Automatically add `grow` to text components (see isAddGrowX).
 */
internal class MigLayoutBuilder : LayoutBuilderImpl {
  private val rows = SmartList<Row>()

  override fun addRow(row: Row) {
    rows.add(row)
  }

  override fun noteRow(text: String) {
    // add empty row
    addRow(Row(null))

    val row = Row(null, noGrid = true)
    row.apply { (noteComponent(text))() }
    addRow(row)
  }

  override fun build(container: Container) {
    val labeled = rows.firstOrNull(Row::labeled) != null
    var gapTop = -1

    container.layout = MigLayout(c().fillX())

    for (row in rows) {
      val lastComponent = row.components.lastOrNull()
      if (lastComponent == null) {
        if (row === rows.first()) {
          // do not add gap for the first row
          continue
        }

        // https://docs.google.com/document/d/1DKnLkO-7_onA7_NCw669aeMH5ltNvw-QMiQHnXu8k_Y/edit#heading=h.c3849zu3vjhq
        // gap = 10u where u = 4px
        gapTop = VERTICAL_GAP * 3
      }

      for ((index, component) in row.components.withIndex()) {
        // MigLayout in any case always creates CC, so, create instance even if it is not required
        val cc = CC()

        if (gapTop != -1) {
          cc.vertical.gapBefore = gapToBoundSize(gapTop, false)
          gapTop = -1
        }

        if (row.noGrid) {
          if (component === lastComponent) {
            cc.wrap()
          }
          if (component === row.components.first()) {
            // rowConstraints.noGrid() doesn't work correctly
            cc.spanX()
          }
        }
        else {
          if (isAddGrowX(component)) {
            cc.growX()
          }

          if (component === lastComponent) {
            cc.wrap()
            // set span for last component because cell count in other rows may be greater — but we expect that last component can grow
            cc.spanX()
          }

          if (labeled && !row.noGrid && !row.labeled && component === row.components.first()) {
            cc.skip()
          }
        }

        if (index >= row.rightIndex) {
          cc.horizontal.gapBefore = BoundSize(null, null, null, true, null)
        }

        container.add(component, cc)
      }
    }
  }
}

private fun isAddGrowX(component: JComponent) = component is JTextComponent