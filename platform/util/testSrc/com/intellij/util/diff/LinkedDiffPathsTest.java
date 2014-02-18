/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.diff;

import junit.framework.TestCase;

import java.util.BitSet;

/**
 * @author dyoma
 */
public class LinkedDiffPathsTest extends TestCase {
  protected LinkedDiffPaths createPaths(int maxX, int maxY) {
    return new LinkedDiffPaths(maxX, maxY);
  }

  public void testOneDeleteAtferEnd() throws FilesTooBigForDiffException {
    LinkedDiffPaths paths = createPaths(2, 3);
    int key = paths.encodeStep(1, 1, 2, false, -1);
    paths.encodeStep(1, 2, 0, true, key);
    Diff.Change change = decode(paths);
    IntLCSTest.checkLastChange(change, 2, 2, 1, 0);
  }

  public void testOneInsertedAtBegging() throws FilesTooBigForDiffException {
    LinkedDiffPaths paths = createPaths(3, 2);
    paths.encodeStep(2, 1, 2, false, -1);
    Diff.Change change = decode(paths);
    IntLCSTest.checkLastChange(change, 0, 0, 0, 1);
  }

  public void testSingleMiddleChange() throws FilesTooBigForDiffException {
    LinkedDiffPaths paths = createPaths(3, 3);
    int key = paths.encodeStep(0, 0, 1, true, -1);
    key = paths.encodeStep(1, 0, 0, false, key);
    paths.encodeStep(2, 2, 1, true, key);
    IntLCSTest.checkLastChange(decode(paths), 1, 1, 1, 1);
  }

  public void testSingleChangeAtEnd() throws FilesTooBigForDiffException {
    LinkedDiffPaths paths = createPaths(2, 2);
    int key = paths.encodeStep(0, 0, 1, false, -1);
    key = paths.encodeStep(0, 1, 0, true, key);
    paths.encodeStep(1, 1, 0, false, key);
    IntLCSTest.checkLastChange(decode(paths), 1, 1, 1, 1);
  }

  public void testNotSquareChangeAtEnd() throws FilesTooBigForDiffException {
    LinkedDiffPaths paths = createPaths(2, 3);
    int key = paths.encodeStep(0, 0, 1, false, -1);
    key = paths.encodeStep(0, 1, 0, true, key);
    key = paths.encodeStep(0, 2, 0, true, key);
    paths.encodeStep(1, 2, 0, false, key);
    IntLCSTest.checkLastChange(decode(paths), 1, 1, 2, 1);
  }

  private Diff.Change decode(LinkedDiffPaths paths) {
    BitSet[] changes = new BitSet[]{new BitSet(), new BitSet()};
    paths.applyChanges(0, 0, changes[0], changes[1]);
    Reindexer reindexer = new Reindexer();
    reindexer.idInit(paths.getXSize(), paths.getYSize());
    Diff.ChangeBuilder builder = new Diff.ChangeBuilder(0);
    reindexer.reindex(changes, builder);
    return builder.getFirstChange();
  }
}
