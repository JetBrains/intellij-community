package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

class ParserRootInterfaceItem implements Comparable<ParserRootInterfaceItem> {
  final String domain;
  final String name;
  private final ClassNameScheme.Input nameScheme;
  final String fullName;

  public ParserRootInterfaceItem(String domain, String name, ClassNameScheme.Input nameScheme) {
    this.domain = domain;
    this.name = name;
    this.nameScheme = nameScheme;
    fullName = nameScheme.getFullName(domain, name).getFullText();
  }

  void writeCode(TextOutput out) throws IOException {
    out.append("@org.jetbrains.jsonProtocol.JsonParseMethod").newLine();
    out.append("public abstract ").append(fullName).space();
    appendReadMethodName(out);
    out.append("(").append(Util.JSON_READER_PARAMETER_DEF).append(")").semi().newLine();
  }

  void appendReadMethodName(TextOutput out) {
    out.append(nameScheme.getParseMethodName(domain, name));
  }

  @Override
  public int compareTo(@NotNull ParserRootInterfaceItem o) {
    return fullName.compareTo(o.fullName);
  }
}
