package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;

class ListType extends BoxableType {
  private final BoxableType itemType;

  public ListType(@NotNull BoxableType itemType) {
    this.itemType = itemType;
  }

  @Override
  String getWriteMethodName() {
    if (itemType == STRING) {
      return "writeStringList";
    }
    else if (itemType == LONG) {
      return "writeLongArray";
    }
    else if (itemType == INT) {
      return "writeIntArray";
    }
    else if (itemType == NUMBER) {
      return "writeDoubleArray";
    }
    return "writeList";
  }

  @Override
  String getFullText() {
    if (itemType == LONG || itemType == INT || itemType == NUMBER) {
      return itemType.getFullText() + "[]";
    }
    return "java.util.List<" + itemType.getFullText() + '>';
  }

  @Override
  String getShortText(NamePath contextNamespace) {
    if (itemType == LONG || itemType == INT || itemType == NUMBER) {
      return itemType.getFullText() + "[]";
    }
    return "java.util.List<" + itemType.getShortText(contextNamespace) + '>';
  }
}