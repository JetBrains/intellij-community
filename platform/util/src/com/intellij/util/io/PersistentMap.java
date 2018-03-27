package com.intellij.util.io;

import com.intellij.util.Processor;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Dmitry Avdeev
 */
public interface PersistentMap<K, V> extends Closeable {

  V get(K key) throws IOException;

  void put(K key, V value) throws IOException;

  boolean processKeys(Processor<K> processor) throws IOException;

  boolean isClosed();

  boolean isDirty();

  void force();

  void close() throws IOException;

  void markDirty() throws IOException;
}
