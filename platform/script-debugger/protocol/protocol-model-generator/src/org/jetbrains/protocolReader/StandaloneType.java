package org.jetbrains.protocolReader;

class StandaloneType extends BoxableType {
  private final NamePath namePath;
  private final String writeMethodName;

  StandaloneType(NamePath namePath, String writeMethodName) {
    this.namePath = namePath;
    this.writeMethodName = writeMethodName;
  }

  @Override
  String getWriteMethodName() {
    return writeMethodName;
  }

  @Override
  CharSequence getFullText() {
    return namePath.getFullText();
  }

  @Override
  String getShortText(NamePath contextNamespace) {
    int nameLength = namePath.getLength();
    int contextLength = contextNamespace.getLength();
    if (nameLength > contextLength) {
      StringBuilder builder = subtractContextRecursively(namePath, nameLength - contextLength, contextNamespace);
      if (builder != null) {
        return builder.toString();
      }
    }
    return namePath.getFullText();
  }

  private static StringBuilder subtractContextRecursively(NamePath namePos, int count, NamePath prefix) {
    if (count > 1) {
      StringBuilder result = subtractContextRecursively(namePos.getParent(), count - 1, prefix);
      if (result == null) {
        return null;
      }
      result.append('.');
      result.append(namePos.getLastComponent());
      return result;
    }
    else {
      String nameComponent = namePos.getLastComponent();
      namePos = namePos.getParent();
      do {
        if (!namePos.getLastComponent().equals(prefix.getLastComponent())) {
          return null;
        }
        namePos = namePos.getParent();
        prefix = prefix.getParent();
      }
      while (namePos != null);

      StringBuilder result = new StringBuilder();
      result.append(nameComponent);
      return result;
    }
  }
}