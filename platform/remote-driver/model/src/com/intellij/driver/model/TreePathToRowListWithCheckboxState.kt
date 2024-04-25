package com.intellij.driver.model

import com.intellij.driver.model.transport.PassByValue
import java.io.Serializable

class TreePathToRowListWithCheckboxState(path: List<String>, row: Int, val checkboxState: Boolean) : TreePathToRow(path, row) {

  override fun toString(): String {
    return "TreePathToRow{" +
           "path=" + path + "; " +
           "row=" + row + "; " +
           "checkboxState=" + checkboxState +
           '}'
  }
}

class TreePathToRowListWithCheckboxStateList : ArrayList<TreePathToRowListWithCheckboxState>(), Serializable, PassByValue
