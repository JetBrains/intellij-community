package com.intellij.util.io;

import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Avdeev
 *         Date: 8/10/11
 */
public interface PersistentMap<K, V> extends Closeable {

  boolean useIndexServer = System.getProperty("use-index-server") != null;

  V get(K key) throws IOException;

  void put(K key, V value) throws IOException;

  void remove(K key) throws IOException;

  boolean processKeys(Processor<K> processor) throws IOException;

  boolean isClosed();

  boolean isDirty();

  void force();

  void close() throws IOException;

  void markDirty() throws IOException;

  void clear() throws IOException;
}

