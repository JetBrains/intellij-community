package org.jetbrains.debugger.values;

/**
 * Don't forget to update NashornDebuggerSupport.ValueType respectively also
 */
public enum ValueType {
  OBJECT,
  NUMBER,
  STRING,
  FUNCTION,
  BOOLEAN,

  ARRAY,
  REGEXP,
  DATE,
  NODE,

  /**
   * undefined type.
   */
  UNDEFINED,

  /**
   * null type. This is a bogus type that doesn't exist in JavaScript.
   */
  NULL;

  /**
   * Returns whether {@code type} corresponds to a JsObject. Note that while 'null' is an object
   * in JavaScript world, here for API consistency it has bogus type {@link #NULL} and is
   * not a {@link ObjectValue}
   */
  public boolean isObjectType() {
    return this == OBJECT || this == ARRAY || this == FUNCTION || this == REGEXP || this == DATE || this == NODE;
  }
}