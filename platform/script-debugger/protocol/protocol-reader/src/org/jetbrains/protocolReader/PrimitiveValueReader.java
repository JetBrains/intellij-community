package org.jetbrains.protocolReader;

class PrimitiveValueReader extends ValueReader {
  private final String className;
  private final String readPostfix;
  final String defaultValue;

  private final boolean asRawString;

  PrimitiveValueReader(String name) {
    this(name, false);
  }

  PrimitiveValueReader(String name, boolean nullable) {
    this(name, null, nullable, false);
  }

  PrimitiveValueReader(String name, String defaultValue) {
    this(name, defaultValue, false, false);
  }

  public PrimitiveValueReader(String name, String defaultValue, boolean nullable, boolean asRawString) {
    super(nullable);

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
  void writeReadCode(ClassScope methodScope, boolean subtyping, String fieldName, TextOutput out) {
    if (asRawString) {
      out.append("readRawString(");
      addReaderParameter(subtyping, out);
    }
    else {
      beginReadCall(readPostfix, subtyping, out, fieldName);
    }
    out.append(')');
  }

  @Override
  void appendFinishedValueTypeName(TextOutput out) {
    out.append(className);
  }

  @Override
  public void writeArrayReadCode(ClassScope scope,
                                 boolean subtyping,
                                 boolean nullable,
                                 String fieldName, TextOutput out) {
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
