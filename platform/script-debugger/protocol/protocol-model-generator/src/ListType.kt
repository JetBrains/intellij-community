package org.jetbrains.protocolModelGenerator

open class ListType(private val itemType: BoxableType) : BoxableType() {

  override val writeMethodName: String
    get() = when {
      itemType == BoxableType.STRING -> {
        "writeStringList"
      }
      itemType == BoxableType.LONG -> "writeLongArray"
      itemType == BoxableType.INT -> "writeIntArray"
      itemType == BoxableType.NUMBER ->  "writeDoubleArray"
      itemType == BoxableType.NUMBER -> "writeDoubleArray"
      itemType is StandaloneType && itemType.writeMethodName == "writeEnum" -> "writeEnumList"
      else -> "writeList"
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
    return "java.util.List<${itemType.getShortText(contextNamespace)}>"
  }
}