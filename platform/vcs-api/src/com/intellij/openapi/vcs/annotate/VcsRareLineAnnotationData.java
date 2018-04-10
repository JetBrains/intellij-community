/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;

import java.util.HashMap;
import java.util.Map;

/**
 * @author irengrig
 */
public class VcsRareLineAnnotationData implements VcsLineAnnotationData {
  private final Map<Integer, VcsRevisionNumber> myMap;
  private final int mySize;

  public VcsRareLineAnnotationData(final int size) {
    mySize = size;
    myMap = new HashMap<>();
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
