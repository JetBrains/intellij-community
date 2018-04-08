// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.fixtures

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.runOnEdt

/**
 * @author Artem.Gainanov
 */
class GutterFixture(private val myIde: IdeFrameFixture) {

  enum class GutterIcon {
    BREAKPOINT,
    DISABLED_BREAKPOINT,
    CSS_COLOR,
    RUN_SCRIPT,
    IMPLEMENTATION,
    IMPLEMENTED,
    OVERRIDES,
    OVERRIDDEN
  }

  private var gutterIcons: MutableMap<GutterIcon, ArrayList<Int>> = mutableMapOf()

  /**
   * Returns the list of lines with given gutterIcon
   * @param gutterIcon - gutter icon class
   *
   * @see GutterFixture.GutterIcon
   */
  fun getLinesWithGutterIcon(gutterIcon: GutterIcon): ArrayList<Int> {
    getGutterIcons()
    return gutterIcons[gutterIcon]!!
  }

  /**
   * Returns true if the line has given gutterIcon
   * @param line - 1-based line number
   * @param gutterIcon - gutter icon class
   *
   * @see GutterFixture.GutterIcon
   */
  fun containsGutterIcon(gutterIcon: GutterIcon, line: Int): Boolean {
    getGutterIcons()
    return gutterIcons[gutterIcon]!!.contains(line)
  }

  /**
   * Returns the map of gutter icon and the list of lines with this icon
   * For example {BREAKPOINT=[], DISABLED_BREAKPOINT=[], CSS_COLOR=[], RUN_SCRIPT=[],
   * IMPLEMENTATION=[14, 16, 15], IMPLEMENTED=[2, 1, 4, 3], OVERRIDES=[17],
   * OVERRIDDEN=[7, 8]}
   */
  fun getGutterIcons(): MutableMap<GutterIcon, ArrayList<Int>> {
    for (icon in GutterIcon.values()) {
      gutterIcons[icon] = ArrayList()
    }
    runOnEdt {
      for (high in getMarkupModel().allHighlighters) {
        val lineNumber = myIde.editor.editor.document.getLineNumber(high.startOffset) + 1
        if (high.gutterIconRenderer?.icon != null) {
          if (high.gutterIconRenderer?.icon.toString() == "com.intellij.xml.util.ColorIconCache\$ColorIcon 8x8") {
            gutterIcons[GutterIcon.CSS_COLOR]!!.add(lineNumber)
          }
          when (high.gutterIconRenderer?.icon) {
            AllIcons.Gutter.ImplementingMethod -> gutterIcons[GutterIcon.IMPLEMENTATION]!!.add(lineNumber)
            AllIcons.Gutter.OverridenMethod -> gutterIcons[GutterIcon.OVERRIDDEN]!!.add(lineNumber)
            AllIcons.Gutter.ImplementedMethod -> gutterIcons[GutterIcon.IMPLEMENTED]!!.add(lineNumber)
            AllIcons.Gutter.OverridingMethod -> gutterIcons[GutterIcon.OVERRIDES]!!.add(lineNumber)
            AllIcons.RunConfigurations.TestState.Run -> gutterIcons[GutterIcon.RUN_SCRIPT]!!.add(lineNumber)
            AllIcons.Debugger.Db_set_breakpoint -> gutterIcons[GutterIcon.BREAKPOINT]!!.add(lineNumber)
            AllIcons.Debugger.Db_disabled_breakpoint -> gutterIcons[GutterIcon.DISABLED_BREAKPOINT]!!.add(lineNumber)
          }
        }
      }
    }
    return gutterIcons
  }

  private fun getMarkupModel(): MarkupModel {
    val document = myIde.editor.editor.document
    return DocumentMarkupModel.forDocument(document, myIde.project, true)
  }
}