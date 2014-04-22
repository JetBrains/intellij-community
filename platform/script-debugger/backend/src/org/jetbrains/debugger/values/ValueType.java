package org.jetbrains.debugger.values;

import org.jetbrains.annotations.NotNull;

/**
 * Don't forget to update NashornDebuggerSupport.ValueType and DebuggerSupport.ts respectively also
 */
public enum ValueType {
  OBJECT,
  NUMBER,
  STRING,
  FUNCTION,
  BOOLEAN,

  ARRAY,
  NODE,

  UNDEFINED,
  NULL;

  private static final ValueType[] VALUE_TYPES = ValueType.values();

  @NotNull
  public static ValueType fromIndex(int index) {
    return VALUE_TYPES[index];
  }

  /**
   * Returns whether {@code type} corresponds to a JsObject. Note that while 'null' is an object
   * in JavaScript world, here for API consistency it has bogus type {@link #NULL} and is
   * not a {@link ObjectValue}
   */
  public boolean isObjectType() {
    return this == OBJECT || this == ARRAY || this == FUNCTION || this == NODE;
  }
}