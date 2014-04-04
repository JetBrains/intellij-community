package org.jetbrains.protocolReader;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;

public class Util {
  public static final char TYPE_NAME_PREFIX = 'M';
  public static final char TYPE_FACTORY_NAME_POSTFIX = 'F';

  public static final String READER_NAME = "reader";
  public static final String PENDING_INPUT_READER_NAME = "inputReader";

  public static final String BASE_VALUE_PREFIX = "baseMessage";

  public static final String JSON_READER_CLASS_NAME = "org.jetbrains.io.JsonReaderEx";
  public static final String JSON_READER_PARAMETER_DEF = JSON_READER_CLASS_NAME + " " + READER_NAME;

  /**
   * Generate Java type name of the passed type. Type may be parameterized.
   */
  public static void writeJavaTypeName(Type arg, TextOutput out) {
    if (arg instanceof Class) {
      out.append(((Class<?>)arg).getCanonicalName());
    }
    else if (arg instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)arg;
      writeJavaTypeName(parameterizedType.getRawType(), out);
      out.append('<');
      Type[] params = parameterizedType.getActualTypeArguments();
      for (int i = 0; i < params.length; i++) {
        if (i != 0) {
          out.comma();
        }
        writeJavaTypeName(params[i], out);
      }
      out.append('>');
    }
    else if (arg instanceof WildcardType) {
      WildcardType wildcardType = (WildcardType)arg;
      Type[] upperBounds = wildcardType.getUpperBounds();
      if (upperBounds == null) {
        throw new RuntimeException();
      }
      if (upperBounds.length != 1) {
        throw new RuntimeException();
      }
      out.append("? extends ");
      writeJavaTypeName(upperBounds[0], out);
    }
    else {
      out.append(String.valueOf(arg));
    }
  }
}