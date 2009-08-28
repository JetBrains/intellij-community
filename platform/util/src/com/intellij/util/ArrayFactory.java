/*
 * @author max
 */
package com.intellij.util;

public interface ArrayFactory<T> {
  T[] create(int count);
}