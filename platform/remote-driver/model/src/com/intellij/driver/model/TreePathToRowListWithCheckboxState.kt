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

class TreePathToRowListWithCheckboxStateList : ArrayList<TreePathToRowListWithCheckboxState>(), Serializable, PassByValue {
  fun getCheckboxStateByPath(path: List<String>): Boolean {
    val checkbox = this.firstOrNull { it.path.equals(path) }
    if (checkbox == null) {
      throw IllegalStateException("Can't find checkbox with path: $path")
    }
    return checkbox.checkboxState
  }
}
