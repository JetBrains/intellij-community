package com.intellij.openapi.vcs;

import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @deprecated Use {@link JBIterable#from(Iterable)} and {@link JBIterable#partition(int, boolean)} directly.
 */
@SuppressWarnings("unused") // Required for compatibility with external plugins.
@Deprecated
public class CollectionSplitter<T> {
  private final int myBunchSize;

  public CollectionSplitter(int bunchSize) {
    myBunchSize = bunchSize;
  }

  @NotNull
  public List<List<T>> split(@NotNull Collection<T> in) {
    return JBIterable.from(in).partition(myBunchSize, false).toList();
  }
}
