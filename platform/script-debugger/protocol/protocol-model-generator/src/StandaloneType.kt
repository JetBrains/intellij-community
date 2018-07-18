package org.jetbrains.protocolModelGenerator

class StandaloneType(private val namePath: NamePath, override val writeMethodName: String, override val defaultValue: String? = "null") : BoxableType {
  override val fullText: String = namePath.getFullText()

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
    if (count > 1) {
      val result = subtractContextRecursively(namePos!!.parent, count - 1, prefix) ?: return null
      result.append('.')
      result.append(namePos.lastComponent)
      return result
    }
    else {
      var namePos = namePos
      var prefix = prefix
      val nameComponent = namePos!!.lastComponent
      namePos = namePos.parent
      do {
        if (namePos!!.lastComponent != prefix.lastComponent) {
          return null
        }

        namePos = namePos.parent
        if (namePos == null) {
          break
        }

        prefix = prefix.parent!!
      }
      while (true)

      val result = StringBuilder()
      result.append(nameComponent)
      return result
    }
  }
}