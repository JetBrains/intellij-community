package com.intellij.driver.sdk.plugins.notebooks

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.singleProject

@Remote("com.intellij.jupyter.core.jupyter.NotebookEditorInfoService", plugin = "intellij.jupyter/intellij.jupyter.core")
interface NotebookEditorInfoService {
  fun getSelectedCellOrdinal(editor: Editor): Int?

  fun getCellsSize(editor: Editor): Int

  // returns "Ok" if the cell is executed successfully, "Error" if execution failed, and null for any other state
  fun getCellExecutionState(cellIndex: Int): String?

  fun setCurrentCellText(text: String)
}

fun Driver.setCurrentCellText(text: String) = service<NotebookEditorInfoService>(singleProject()).setCurrentCellText(text)