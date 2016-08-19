package com.intellij.openapi.vcs;

import java.util.*;

public class CollectionSplitter<T> {
  private final int myBunchSize;

  public CollectionSplitter(int bunchSize) {
    myBunchSize = bunchSize;
  }

  public List<List<T>> split(final Collection<T> in) {
    if (in.size() <= myBunchSize) return Collections.<List<T>>singletonList(new ArrayList<>(in));

    final List<List<T>> result = new LinkedList<>();
    List<T> piece = new LinkedList<>();
    for (T path : in) {
      if (myBunchSize == piece.size()) {
        result.add(piece);
        piece = new LinkedList<>();
      }
      piece.add(path);
    }
    if (! piece.isEmpty()) {
      result.add(piece);
    }
    return result;
  }
}
