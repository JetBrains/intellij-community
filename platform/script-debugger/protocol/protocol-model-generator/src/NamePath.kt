package org.jetbrains.protocolModelGenerator

data class NamePath(val lastComponent: String, val parent: NamePath? = null) {
  fun getLength(): Int {
    var res = 1
    run {
      var current: NamePath? = this
      while (current != null) {
        res++
        current = current.parent
      }
    }
    return res
  }

  fun getFullText(): String {
    val result = StringBuilder()
    fillFullPath(result)
    return result.toString()
  }

  private fun fillFullPath(result: StringBuilder) {
    if (parent != null) {
      parent.fillFullPath(result)
      result.append('.')
    }
    result.append(lastComponent)
  }
}
