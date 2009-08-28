package com.intellij.util;

public interface ThrowableConvertor<U,V, T extends Throwable> {
  V convert(U u) throws T;
}
