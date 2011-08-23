package com.intellij.util.io;

import com.intellij.util.Processor;

import java.io.IOException;

/**
 * @author Dmitry Avdeev
 *         Date: 8/10/11
 */
public interface PersistentMap<K, V> {
  
  V get(K key) throws IOException;

  void put(K key, V value) throws IOException;

  boolean processKeys(Processor<K> processor) throws IOException;


  boolean isClosed();

  boolean isDirty();

  void force();

  void close() throws IOException;

  void markDirty() throws IOException;
}
