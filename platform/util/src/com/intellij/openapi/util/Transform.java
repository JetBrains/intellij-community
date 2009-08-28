package com.intellij.openapi.util;

public interface Transform <S, T> {
  T transform(S s);
}