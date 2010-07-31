package com.intellij.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public class ParameterizedTypeImpl implements ParameterizedType {
  private final Type myRawType;
  private final Type[] myArguments;

  public ParameterizedTypeImpl(Type rawType, Type... arguments) {
    myRawType = rawType;
    myArguments = arguments;
  }

  @Override
  public Type[] getActualTypeArguments() {
    return myArguments;
  }

  @Override
  public Type getRawType() {
    return myRawType;
  }

  @Override
  public Type getOwnerType() {
    return null;
  }
}
