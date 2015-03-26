package org.jetbrains.protocolReader

class StandaloneType(private val namePath: NamePath, private val writeMethodName: String) : BoxableType() {
  override fun getWriteMethodName(): String {
    return writeMethodName
  }

  override fun getFullText(): CharSequence {
    return namePath.getFullText()
  }

  override fun getShortText(contextNamespace: NamePath): String {
    val nameLength = namePath.getLength()
    val contextLength = contextNamespace.getLength()
    if (nameLength > contextLength) {
      val builder = subtractContextRecursively(namePath, nameLength - contextLength, contextNamespace)
      if (builder != null) {
        return builder.toString()
      }
    }
    return namePath.getFullText()
  }

  private fun subtractContextRecursively(namePos: NamePath?, count: Int, prefix: NamePath): StringBuilder? {
    var namePos = namePos
    var prefix = prefix
    if (count > 1) {
      val result = subtractContextRecursively(namePos!!.parent, count - 1, prefix)
      if (result == null) {
        return null
      }
      result.append('.')
      result.append(namePos!!.lastComponent)
      return result
    }
    else {
      val nameComponent = namePos!!.lastComponent
      namePos = namePos!!.parent
      do {
        if (namePos!!.lastComponent != prefix.lastComponent) {
          return null
        }
        namePos = namePos!!.parent
        prefix = prefix.parent!!
      }
      while (namePos != null)

      val result = StringBuilder()
      result.append(nameComponent)
      return result
    }
  }
}