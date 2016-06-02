package pkg;

public @interface MoreAnnotations {

  int intValue() default 1;
  byte byteValue() default 1;
  float floatValue() default Float.POSITIVE_INFINITY;
  double doubleValue() default Double.NaN;
  boolean booleanValue() default true;
  short shortValue() default 1;
  long longValue() default 1;
  char charValue() default '0';
  TestEnum enumValue() default TestEnum.FirstValue;
  NestedAnnotation annotationValue() default @NestedAnnotation;
  String stringValue() default "default";
  Class<? extends CharSequence> classValue() default CharSequence.class;

  int[] intArray() default { 1, 0, Integer.MAX_VALUE, Integer.MIN_VALUE };
  byte[] byteArray() default { 1, 0, Byte.MAX_VALUE, Byte.MIN_VALUE, (byte)0xFF };
  float[] floatArray() default { 1, 0, Float.MAX_VALUE, Float.MIN_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY };
  double[] doubleArray() default {  1, 0, Double.MAX_VALUE, Double.MIN_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY };
  boolean[] booleanArray() default { true, false };
  short[] shortArray() default { 1, 0, Short.MAX_VALUE, Short.MIN_VALUE, (short)0xFFFF };
  long[] longArray() default { 1, 0, Long.MAX_VALUE, Long.MIN_VALUE };
  char[] charArray() default { 1, 0, Character.MAX_VALUE, Character.MIN_VALUE };
  TestEnum[] enumArray() default { TestEnum.FirstValue };
  NestedAnnotation[] annotationArray() default { @NestedAnnotation };
  String[] stringArray() default { "first", "second", "" };
  Class<? extends CharSequence>[] classArray() default { CharSequence.class, String.class, StringBuilder.class };

  @interface NestedAnnotation {
    String value() default "MyString";
  }

  @MoreAnnotations(
    intValue = 1,
    byteValue = 1,
    floatValue = 1,
    doubleValue = 1,
    booleanValue = true,
    shortValue = 1,
    longValue = 1,
    charValue = '\n',
    enumValue = TestEnum.FirstValue,
    annotationValue = @NestedAnnotation("a"),
    stringValue = "",
    classValue = String.class
  )
  String annotatedWithValues = "";

  @MoreAnnotations(
    intArray = {},
    byteArray = {},
    floatArray = {},
    doubleArray = {},
    booleanArray = {},
    shortArray = {},
    longArray = {},
    charArray = {},
    enumArray = {},
    annotationArray = {},
    stringArray =  {},
    classArray = {}
  )
  String annotatedWithEmptyArrays = "";

  @MoreAnnotations(
    intArray = { 1, 0, Integer.MAX_VALUE, Integer.MIN_VALUE },
    byteArray = { 1, 0, Byte.MAX_VALUE, Byte.MIN_VALUE, (byte)0xFF },
    floatArray = { 1, 0, Float.MAX_VALUE, Float.MIN_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY },
    doubleArray = {  1, 0, Double.MAX_VALUE, Double.MIN_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY },
    booleanArray = { true, false },
    shortArray = { 1, 0, Short.MAX_VALUE, Short.MIN_VALUE, (short)0xFFFF },
    longArray = { 1, 0, Long.MAX_VALUE, Long.MIN_VALUE },
    charArray = { 'a', '\n', 1, 0, Character.MAX_VALUE, Character.MIN_VALUE },
    enumArray = { TestEnum.FirstValue , TestEnum.SecondValue },
    annotationArray = { @NestedAnnotation("a"), @NestedAnnotation("b") },
    stringArray = { "first", "second", "" },
    classArray = { CharSequence.class, String.class, StringBuilder.class }
  )
  String annotatedWithArrays = "";

  public enum TestEnum {

    FirstValue,
    SecondValue

  }
}
