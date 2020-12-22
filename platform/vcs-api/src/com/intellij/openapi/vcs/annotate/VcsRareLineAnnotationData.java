// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * @author irengrig
 */
public final class VcsRareLineAnnotationData implements VcsLineAnnotationData {
  private final Int2ObjectMap<VcsRevisionNumber> myMap = new Int2ObjectOpenHashMap<>();
  private final int mySize;

  public VcsRareLineAnnotationData(int size) {
    mySize = size;
  }

  @Override
  public void put(int lineNumber, VcsRevisionNumber revisionNumber) {
    assert lineNumber >= 0 && mySize > lineNumber;
    myMap.put(lineNumber, revisionNumber);
  }

  @Override
  public int getNumLines() {
    return mySize;
  }

  @Override
  public VcsRevisionNumber getRevision(int lineNumber) {
    assert lineNumber >= 0 && mySize > lineNumber;
    return myMap.get(lineNumber);
  }

  public boolean isEmpty() {
    return myMap.isEmpty();
  }
}
