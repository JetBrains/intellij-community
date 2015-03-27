package org.jetbrains.protocolModelGenerator

open class ListType(private val itemType: BoxableType) : BoxableType() {

  override fun getWriteMethodName(): String {
    if (itemType == BoxableType.STRING) {
      return "writeStringList"
    }
    else if (itemType == BoxableType.LONG) {
      return "writeLongArray"
    }
    else if (itemType == BoxableType.INT) {
      return "writeIntArray"
    }
    else if (itemType == BoxableType.NUMBER) {
      return "writeDoubleArray"
    }
    return "writeList"
  }

  override fun getFullText(): String {
    if (itemType == BoxableType.LONG || itemType == BoxableType.INT || itemType == BoxableType.NUMBER) {
      return "${itemType.getFullText()}[]"
    }
    return "java.util.List<" + itemType.getFullText() + '>'
  }

  override fun getShortText(contextNamespace: NamePath): String {
    if (itemType == BoxableType.LONG || itemType == BoxableType.INT || itemType == BoxableType.NUMBER) {
      return "${itemType.getFullText()}[]"
    }
    return "java.util.List<" + itemType.getShortText(contextNamespace) + '>'
  }
}