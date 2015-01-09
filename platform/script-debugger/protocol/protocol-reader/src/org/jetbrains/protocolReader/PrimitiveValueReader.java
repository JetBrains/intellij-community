package org.jetbrains.protocolReader;

import org.jetbrains.annotations.NotNull;

class PrimitiveValueReader extends ValueReader {
  private final String className;
  private final String readPostfix;
  final String defaultValue;

  private final boolean asRawString;

  PrimitiveValueReader(String name) {
    this(name, null, false);
  }

  PrimitiveValueReader(String name, String defaultValue) {
    this(name, defaultValue, false);
  }

  public PrimitiveValueReader(String name, String defaultValue, boolean asRawString) {
    super();

    this.defaultValue = defaultValue;
    this.asRawString = asRawString;
    className = name;
    if (Character.isLowerCase(className.charAt(0))) {
      readPostfix = Character.toUpperCase(className.charAt(0)) + className.substring(1);
    }
    else {
      readPostfix = asRawString ? ("Raw" + className) : className;
    }
  }

  @Override
  void writeReadCode(ClassScope methodScope, boolean subtyping, @NotNull TextOutput out) {
    if (asRawString) {
      out.append("readRawString(");
      addReaderParameter(subtyping, out);
      out.append(')');
    }
    else {
      ValueReader.addReaderParameter(subtyping, out);
      out.append(".next").append(readPostfix).append("()");
      //beginReadCall(readPostfix, subtyping, out, name);
    }
  }

  @Override
  void appendFinishedValueTypeName(@NotNull TextOutput out) {
    out.append(className);
  }

  @Override
  public void writeArrayReadCode(@NotNull ClassScope scope,
                                 boolean subtyping,
                                 @NotNull TextOutput out) {
    if (readPostfix.equals("String")) {
      out.append("nextList");
    }
    else {
      out.append("read").append(readPostfix).append("Array");
    }
    out.append('(').append(Util.READER_NAME);
    out.append(')');
  }
}
