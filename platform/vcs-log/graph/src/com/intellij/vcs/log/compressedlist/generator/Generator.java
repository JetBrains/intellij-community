package com.intellij.vcs.log.compressedlist.generator;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface Generator<T> {
  /**
   * @throws java.util.NoSuchElementException
   *
   * @throws IllegalArgumentException
   */
  @NotNull
  public T generate(@NotNull T prev, int steps);

  @NotNull
  public T generateFirst();
}
