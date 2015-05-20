package org.jetbrains.jsonProtocol;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes a method that corresponds to a type field (i.e. a property of JSON object).
 * Its use is optional, because all methods by default are recognized as field-reading methods.
 * Should be used to specify JSON property name.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonField {
  /**
   * Specifies JSON property name, which otherwise is derived from the method name (optional "get"
   * prefix is truncated with the first letter decapitalization).
   */
  String name() default "";

  // read any primitive value as String (true as true, number as string - don't try to parse)
  boolean allowAnyPrimitiveValue() default false;

  boolean allowAnyPrimitiveValueAndMap() default false;

  boolean optional() default false;

  String primitiveValue() default "";
}