package org.jetbrains.protocolModelGenerator

open class ListType(private val itemType: BoxableType) : BoxableType {
  override val defaultValue: Nothing? = null

  override val writeMethodName: String
    get() = when {
      itemType == BoxableType.STRING -> "writeStringList"
      itemType == BoxableType.LONG -> "writeLongArray"
      itemType == BoxableType.INT -> "writeIntArray"
      itemType == BoxableType.NUMBER ->  "writeDoubleArray"
      itemType == BoxableType.NUMBER -> "writeDoubleArray"
      itemType is StandaloneType && itemType.writeMethodName == "writeEnum" -> "writeEnumList"
      else -> "writeList"
    }

  override val fullText: CharSequence
    get() {
    if (itemType == BoxableType.LONG || itemType == BoxableType.INT || itemType == BoxableType.NUMBER) {
      return "Array<${itemType.fullText}>"
    }
    return "List<${itemType.fullText}>"
  }

  override fun getShortText(contextNamespace: NamePath): String {
    if (itemType == BoxableType.LONG || itemType == BoxableType.INT || itemType == BoxableType.NUMBER) {
      return "${itemType.fullText}Array"
    }
    return "List<${itemType.getShortText(contextNamespace)}>"
  }
}