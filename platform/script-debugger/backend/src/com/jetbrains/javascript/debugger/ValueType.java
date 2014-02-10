package com.jetbrains.javascript.debugger;

/**
 * Type of a JavaScript value. Two bogus type values (DATE and ARRAY) are
 * included even though they are not reported by V8. Instead, they are inferred
 * from the object classname. {@code null} value has a bogus type NULL.
 */
public enum ValueType {
  /**
   * Object type.
   */
  OBJECT,

  /**
   * Number type.
   */
  NUMBER,

  /**
   * String type.
   */
  STRING,

  /**
   * Function type.
   */
  FUNCTION,

  /**
   * Boolean type.
   */
  BOOLEAN,

  /**
   * Error type (this one describes a JavaScript exception).
   */
  ERROR,

  /**
   * A regular expression.
   */
  REGEXP,

  /**
   * An object which is actually a Date. This type is not present in the
   * protocol but is rather induced from the "object" type and "Date" class of
   * an object.
   */
  DATE,

  /**
   * An object which is actually an array. This type is not present in the
   * protocol but is rather induced from the "object" type and "Array" class of
   * an object.
   */
  ARRAY,

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
    return this == OBJECT || this == ARRAY || this == ERROR || this == FUNCTION || this == REGEXP || this == DATE;
  }
}