/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore.type;

import io.netty.buffer.ByteBuf;

/**
 * A data type.
 */
public interface DataType<T> {
  /**
   * Calculates the amount of used memory in bytes.
   *
   * @param obj the object
   * @return the used memory
   */
  int getMemory(T obj);

  /**
   * Whether size of instance of this type is constant  or variable.
   * Return -1 if variable.
   */
  int getFixedMemory();

  default int getMemory(T[] objects) {
    int fixedSize = getFixedMemory();
    if (fixedSize != -1) {
      return objects.length * fixedSize;
    }

    int result = 0;
    for (T object : objects) {
      result += getMemory(object);
    }
    return result;
  }

  /**
   * Both a and b are not not nullable.
   */
  default boolean equals(T a, T b) {
    return a.equals(b);
  }

  /**
   * Write an object.
   *
   * @param buf the target buffer
   * @param obj the value
   */
  void write(ByteBuf buf, T obj);

  /**
   * Write a list of objects.
   *
   * @param buf     the target buffer
   * @param storage the objects
   * @param length     the number of objects to write
   */
  default void write(ByteBuf buf, T[] storage, int length) {
    for (int i = 0; i < length; i++) {
      write(buf, storage[i]);
    }
  }

  /**
   * Read an object.
   *
   * @param buff the source buffer
   * @return the object
   */
  T read(ByteBuf buff);

  /**
   * Read a list of objects.
   *
   * @param buf     the target buffer
   * @param storage the objects
   * @param len     the number of objects to read
   */
  default void read(ByteBuf buf, T[] storage, int len) {
    for (int i = 0; i < len; i++) {
      storage[i] = read(buf);
    }
  }

  /**
   * Create storage object of array type to hold values
   *
   * @param size number of values to hold
   * @return storage object
   */
  T[] createStorage(int size);

  default boolean isGenericCompressionApplicable() {
    return true;
  }
}

