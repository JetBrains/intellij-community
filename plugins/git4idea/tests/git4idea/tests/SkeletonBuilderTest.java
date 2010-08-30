/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.tests;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import git4idea.history.wholeTree.CommitHashPlusParents;
import git4idea.history.wholeTree.SkeletonBuilder;
import git4idea.history.wholeTree.TreeSkeleton;
import git4idea.history.wholeTree.TreeSkeletonImpl;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author irengrig
 */
public class SkeletonBuilderTest extends TestCase {
  public void testSeparate() throws Exception {
    final List<CommitHashPlusParents> list = read("1\n2\n3\n4\n5");
    // 4, 4
    final SkeletonBuilder builder = new SkeletonBuilder(2, 2);
    for (CommitHashPlusParents commitHashPlusParents : list) {
      builder.accept(commitHashPlusParents);
    }
    builder.finished();
    final TreeSkeleton result = builder.getResult();
    for (int i = 0; i < 5; i++) {
      final TreeSkeletonImpl.Commit commit = result.getCommitAt(i);
      // just because of the test data order
      Assert.assertEquals("" + (i + 1), new String(commit.getHash()));
      Assert.assertEquals(0, commit.getWireNumber());
    }

    final Iterator<TreeSkeletonImpl.WireEvent> iterator = result.createWireEventsIterator(0);
    for (int i = 0; i < 5; i++) {
      final TreeSkeletonImpl.WireEvent we = iterator.next();
      Assert.assertEquals(true, we.isEnd());
      Assert.assertEquals(true, we.isStart());
      Assert.assertEquals(i, we.getCommitIdx());
    }
  }

  public void testOneLine() throws Exception {
    final List<CommitHashPlusParents> list = read("1 2\n2 3\n3 4\n4 5\n5");
    // 4, 4
    final SkeletonBuilder builder = new SkeletonBuilder(2, 2);
    for (CommitHashPlusParents commitHashPlusParents : list) {
      builder.accept(commitHashPlusParents);
    }
    builder.finished();

    final TreeSkeleton result = builder.getResult();
    for (int i = 0; i < 5; i++) {
      final TreeSkeletonImpl.Commit commit = result.getCommitAt(i);
      // just because of the test data order
      Assert.assertEquals("" + (i + 1), new String(commit.getHash()));
      Assert.assertEquals(0, commit.getWireNumber());
    }

    final Iterator<TreeSkeletonImpl.WireEvent> iterator = result.createWireEventsIterator(0);
    TreeSkeletonImpl.WireEvent we = iterator.next();
    Assert.assertEquals(true, we.isStart());
    Assert.assertEquals(0, we.getCommitIdx());

    we = iterator.next();
    Assert.assertEquals(true, we.isEnd());
    Assert.assertEquals(4, we.getCommitIdx());
  }

  public void testBranchAndMerge() throws Exception {
    final List<CommitHashPlusParents> list = read("1 2\n2 3 4\n3 5\n4 6\n5 7\n6 8\n7 8\n8 9\n9");
    // 4, 4
    final SkeletonBuilder builder = new SkeletonBuilder(2, 2);
    for (CommitHashPlusParents commitHashPlusParents : list) {
      builder.accept(commitHashPlusParents);
    }
    builder.finished();
    final TreeSkeleton result = builder.getResult();
    final int[] expectedWireNumbers = {0, 0, 0, 1, 0, 1, 0, 1, 1};
    for (int i = 0; i < 5; i++) {
      final TreeSkeletonImpl.Commit commit = result.getCommitAt(i);
      // just because of the test data order
      Assert.assertEquals("" + (i + 1), new String(commit.getHash()));
      Assert.assertEquals(expectedWireNumbers[i], commit.getWireNumber());
    }

    final Iterator<TreeSkeletonImpl.WireEvent> iterator = result.createWireEventsIterator(0);
    TreeSkeletonImpl.WireEvent we = iterator.next();
    Assert.assertEquals(true, we.isStart());
    Assert.assertEquals(0, we.getCommitIdx());

    we = iterator.next();
    final int[] commitsStarts = we.getCommitsStarts();
    Assert.assertEquals(2, commitsStarts.length);
    Assert.assertEquals(2, commitsStarts[0]);
    Assert.assertEquals(3, commitsStarts[1]);
    Assert.assertNull(we.getWireEnds());
    Assert.assertEquals(1, we.getCommitIdx());

    we = iterator.next();
    Assert.assertEquals(6, we.getWireEnds()[0]);
    Assert.assertEquals(7, we.getCommitIdx());
    final int[] commitsEnds = we.getCommitsEnds();
    Assert.assertEquals(2, commitsEnds.length);
    Assert.assertEquals(5, commitsEnds[0]);
    Assert.assertEquals(6, commitsEnds[1]);

    we = iterator.next();
    Assert.assertEquals(true, we.isEnd());
    Assert.assertEquals(8, we.getCommitIdx());
  }

  public void testBranchIsMerged() throws Exception {
    final List<CommitHashPlusParents> list = read("1 2 4\n2 3 6\n3 8\n4 5 6\n5 7\n6 9\n7 10\n8 10\n9 10\n 10");
    // 4, 4
    final SkeletonBuilder builder = new SkeletonBuilder(2, 2);
    for (CommitHashPlusParents commitHashPlusParents : list) {
      builder.accept(commitHashPlusParents);
    }
    builder.finished();
    final TreeSkeleton result = builder.getResult();
    final int[] expectedWireNumbers = {0, 0, 0, 1, 1, 2, 1, 0, 2, 1, 1};
    for (int i = 0; i < 5; i++) {
      final TreeSkeletonImpl.Commit commit = result.getCommitAt(i);
      // just because of the test data order
      Assert.assertEquals("" + (i + 1), new String(commit.getHash()));
      Assert.assertEquals(expectedWireNumbers[i], commit.getWireNumber());
    }

    final Iterator<TreeSkeletonImpl.WireEvent> iterator = result.createWireEventsIterator(0);
    TreeSkeletonImpl.WireEvent we = iterator.next();
    Assert.assertEquals(true, we.isStart());
    Assert.assertEquals(0, we.getCommitIdx());
    int[] commitsStarts = we.getCommitsStarts();
    Assert.assertEquals(2, commitsStarts.length);
    Assert.assertEquals(1, commitsStarts[0]);
    Assert.assertEquals(3, commitsStarts[1]);

    we = iterator.next();
    commitsStarts = we.getCommitsStarts();
    Assert.assertEquals(2, commitsStarts.length);
    Assert.assertEquals(2, commitsStarts[0]);
    Assert.assertEquals(5, commitsStarts[1]);
    Assert.assertNull(we.getWireEnds());
    Assert.assertEquals(1, we.getCommitIdx());

    we = iterator.next();
    commitsStarts = we.getCommitsStarts();
    Assert.assertEquals(2, commitsStarts.length);
    Assert.assertEquals(4, commitsStarts[0]);
    Assert.assertEquals(5, commitsStarts[1]);
    Assert.assertNull(we.getWireEnds());
    Assert.assertEquals(3, we.getCommitIdx());

    we = iterator.next();
    Assert.assertNull(we.getWireEnds());
    Assert.assertEquals(5, we.getCommitIdx());
    int[] commitsEnds = we.getCommitsEnds();
    Assert.assertEquals(2, commitsEnds.length);
    Assert.assertEquals(1, commitsEnds[0]);
    Assert.assertEquals(3, commitsEnds[1]);

    we = iterator.next();
    Assert.assertEquals(7, we.getWireEnds()[0]);
    Assert.assertEquals(8, we.getWireEnds()[1]);
    Assert.assertEquals(9, we.getCommitIdx());
    Assert.assertEquals(true, we.isEnd());
    commitsEnds = we.getCommitsEnds();
    Assert.assertEquals(3, commitsEnds.length);
    Assert.assertEquals(6, commitsEnds[0]);
    Assert.assertEquals(7, commitsEnds[1]);
    Assert.assertEquals(8, commitsEnds[2]);
  }

  private List<CommitHashPlusParents> read(final String data) {
    final String[] lines = data.split("\n");
    final List<CommitHashPlusParents> result = new ArrayList<CommitHashPlusParents>(lines.length);
    for (String line : lines) {
      line = line.trim();
      if (StringUtil.isEmptyOrSpaces(line)) continue;
      final int idx = line.indexOf(" ");
      if (idx == -1) {
        result.add(new CommitHashPlusParents(line, ArrayUtil.EMPTY_STRING_ARRAY, 0));
      } else {
        final String[] words = line.substring(idx + 1).split(" ");
        result.add(new CommitHashPlusParents(line.substring(0, idx), words, 0));
      }
    }
    return result;
  }
}
