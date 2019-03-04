package com.intellij.util.io;

import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author Dmitry Avdeev
 */
public interface PersistentMap<K, V> extends KeyValueStore<K, V> {
  boolean processKeys(@NotNull Processor<? super K> processor) throws IOException;

  boolean isClosed();

  boolean isDirty();

  void markDirty() throws IOException;
}
