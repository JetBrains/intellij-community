package com.intellij.vcs.log.compressedlist.generator;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public abstract class AbstractGenerator<T, M extends T> implements Generator<T> {


  @NotNull
  @Override
  public T generate(@NotNull T prev, int steps) {
    if (steps < 0) {
      throw new IllegalStateException("bad steps: " + steps);
    }
    if (steps == 0) {
      return prev;
    }
    M row = this.createMutable(prev);
    for (int i = 0; i < steps; i++) {
      row = oneStep(row);
    }
    return row;
  }

  @NotNull
  protected abstract M createMutable(@NotNull T t);

  /**
   * @throws java.util.NoSuchElementException
   *
   */
  @NotNull
  protected abstract M oneStep(@NotNull M row);
}
