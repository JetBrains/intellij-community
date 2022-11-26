// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ParameterizedTypeImpl)) return false;

    ParameterizedTypeImpl that = (ParameterizedTypeImpl)o;

    if (!Arrays.equals(myArguments, that.myArguments)) return false;
    if (!myRawType.equals(that.myRawType)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myRawType.hashCode();
    result = 31 * result + Arrays.hashCode(myArguments);
    return result;
  }
}
