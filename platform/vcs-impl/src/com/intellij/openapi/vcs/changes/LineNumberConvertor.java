/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import gnu.trove.TIntFunction;
import gnu.trove.TIntHashSet;

import java.util.Map;
import java.util.TreeMap;

/**
* Created by IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 9/8/11
* Time: 1:11 PM
*/
public class LineNumberConvertor implements TIntFunction {
  private final TreeMap<Integer, Integer> myFragmentStarts;
  private final TIntHashSet myEmptyLines;

  public LineNumberConvertor() {
    myFragmentStarts = new TreeMap<>();
    myEmptyLines = new TIntHashSet();
  }

  public void put(final int start, final int offset) {
    myFragmentStarts.put(start, offset);
  }
  
  public void emptyLine(final int line) {
    myEmptyLines.add(line); // real number
  }

  @Override
  public int execute(int o) {
    if (myEmptyLines.contains(o)) return -1;
    final Map.Entry<Integer, Integer> floor = myFragmentStarts.floorEntry(o);
    return floor == null ? o : floor.getValue() + o - floor.getKey();
  }
}
