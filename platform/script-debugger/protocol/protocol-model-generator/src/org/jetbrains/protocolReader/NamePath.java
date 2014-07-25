package org.jetbrains.protocolReader;

class NamePath {
  private final String lastComponent;
  private final NamePath parent;

  NamePath(String component) {
    this(component, null);
  }

  NamePath(String component, NamePath parent) {
    lastComponent = component;
    this.parent = parent;
  }

  NamePath getParent() {
    return parent;
  }

  String getLastComponent() {
    return lastComponent;
  }

  int getLength() {
    int res = 1;
    for (NamePath current = this; current != null; current = current.getParent()) {
      res++;
    }
    return res;
  }

  String getFullText() {
    StringBuilder result = new StringBuilder();
    fillFullPath(result);
    return result.toString();
  }

  private void fillFullPath(StringBuilder result) {
    if (parent != null) {
      parent.fillFullPath(result);
      result.append('.');
    }
    result.append(lastComponent);
  }
}
