// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

import com.intellij.devkit.threading.DevkitThreadingBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.idea.devkit.threadingModelHelper.ConstraintType
import java.util.EnumSet
import javax.swing.event.DocumentEvent

/**
 * The header of the tool window: the number of the shown paths, the constraint type filters, and the search field.
 */
internal class LockReqsHeader(
  onQueryChanged: (String) -> Unit,
  onSelectedTypesChanged: (Set<ConstraintType>) -> Unit,
) {
  private val countLabel = JBLabel()

  private val searchField = SearchTextField(false).apply {
    textEditor.emptyText.text = DevkitThreadingBundle.message("header.search.placeholder")
    textEditor.columns = COLUMNS_MEDIUM
    addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        onQueryChanged(text)
      }
    })
  }

  private val selectedTypes: MutableSet<ConstraintType> = EnumSet.allOf(ConstraintType::class.java)

  val component: DialogPanel = panel {
    row {
      icon(AllIcons.Debugger.ThreadSuspended)
      cell(countLabel)
      for (type in ConstraintType.entries) {
        checkBox(constraintTypeName(type))
          .applyToComponent { isSelected = true }
          .onChanged { checkBox ->
            if (checkBox.isSelected) selectedTypes.add(type) else selectedTypes.remove(type)
            onSelectedTypesChanged(selectedTypes.toSet())
          }
      }
      cell(searchField).align(AlignX.RIGHT)
    }
  }.apply {
    border = JBUI.Borders.compound(IdeBorderFactory.createBorder(SideBorder.BOTTOM), JBUI.Borders.empty(4, 8))
  }

  fun setPathsCount(count: Int) {
    countLabel.text = DevkitThreadingBundle.message("header.paths.count", count)
  }
}
