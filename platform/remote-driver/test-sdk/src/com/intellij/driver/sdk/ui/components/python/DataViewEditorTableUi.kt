package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.common.editorTabs
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent

/** The table of a Data View variable that is opened in an editor tab ('More Actions | Open in Editor Tab'). */
fun Finder.dataViewEditorTable(): JTableUiComponent =
  editorTabs().x(JTableUiComponent::class.java, "Data View table of the editor tab") {
    // Older builds use TableResultView; byJavaClass matches the main GridTable and skips the pinned strip, a subclass.
    or(byType("com.intellij.database.run.ui.table.TableResultView"), byJavaClass("com.intellij.database.run.ui.table.GridTable"))
  }
