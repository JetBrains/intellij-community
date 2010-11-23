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

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ReadonlyList;
import git4idea.history.wholeTree.*;
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
    final TreeNavigationImpl navigation = new TreeNavigationImpl(2, 2);
    final SkeletonBuilder builder = new SkeletonBuilder(navigation);
    final ReadonlyList.ArrayListWrapper<CommitI> commits = new ReadonlyList.ArrayListWrapper<CommitI>();
    fillData(list, navigation, builder, commits);

    for (int i = 0; i < 5; i++) {
      final CommitI commitI = (CommitI)commits.get(i);
      // just because of the test data order
      Assert.assertEquals("" + (i + 1), commitI.getHash().getString());
      Assert.assertEquals(0, commitI.getWireNumber());
    }

    final Iterator<WireEvent> iterator = navigation.createWireEventsIterator(0);
    for (int i = 0; i < 5; i++) {
      final WireEvent we = iterator.next();
      Assert.assertEquals(true, we.isEnd());
      Assert.assertEquals(true, we.isStart());
      Assert.assertEquals(i, we.getCommitIdx());
    }

    assertWires(navigation.getUsedWires(0, commits).getUsed());
    assertWires(navigation.getUsedWires(1, commits).getUsed());
    assertWires(navigation.getUsedWires(2, commits).getUsed());
    assertWires(navigation.getUsedWires(3, commits).getUsed());
    assertWires(navigation.getUsedWires(4, commits).getUsed());

    final Iterator<WireEvent> iterator2 = navigation.createWireEventsIterator(4);
    for (int i = 4; i < 5; i++) {
      final WireEvent we = iterator2.next();
      Assert.assertEquals(true, we.isEnd());
      Assert.assertEquals(true, we.isStart());
      Assert.assertEquals(i, we.getCommitIdx());
    }

    final Iterator<WireEvent> iterator3 = navigation.createWireEventsIterator(2);
    for (int i = 2; i < 5; i++) {
      final WireEvent we = iterator3.next();
      Assert.assertEquals(true, we.isEnd());
      Assert.assertEquals(true, we.isStart());
      Assert.assertEquals(i, we.getCommitIdx());
    }
  }

  private void fillData(List<CommitHashPlusParents> list,
                        TreeNavigationImpl navigation,
                        SkeletonBuilder builder,
                        ReadonlyList.ArrayListWrapper<CommitI> commits) {
    for (CommitHashPlusParents commitHashPlusParents : list) {
      final WireNumberCommitDecoration commitI =
        new WireNumberCommitDecoration(new Commit(commitHashPlusParents.getHash(), commitHashPlusParents.getTime(),
                                                  new Ref<Integer>(-1)));
      commits.getDelegate().add(commitI);
      builder.consume(commitI, getParents(commitHashPlusParents), commits);
    }
    navigation.recountWires(0, commits);
    navigation.recalcIndex(commits);
  }

  private List<AbstractHash> getParents(final CommitHashPlusParents commitHashPlusParents) {
    /*final SmartList<AbstractHash> result = new SmartList<AbstractHash>();
    for (String s : commitHashPlusParents.getParents()) {
      result.add(AbstractHash.create(s));
    }*/
    return commitHashPlusParents.getParents();
  }

  public void testOneLine() throws Exception {
    final List<CommitHashPlusParents> list = read("1 2\n2 3\n3 4\n4 5\n5");
    // 4, 4
    final TreeNavigationImpl navigation = new TreeNavigationImpl(2, 2);
    final SkeletonBuilder builder = new SkeletonBuilder(navigation);
    final ReadonlyList.ArrayListWrapper<CommitI> commits = new ReadonlyList.ArrayListWrapper<CommitI>();
    fillData(list, navigation, builder, commits);

    for (int i = 0; i < 5; i++) {
      final CommitI commitI = (CommitI)commits.get(i);
      // just because of the test data order
      Assert.assertEquals("" + (i + 1), commitI.getHash().getString());
      Assert.assertEquals(0, commitI.getWireNumber());
    }

    final Iterator<WireEvent> iterator = navigation.createWireEventsIterator(0);
    WireEvent we = iterator.next();
    Assert.assertEquals(true, we.isStart());
    Assert.assertEquals(0, we.getCommitIdx());

    we = iterator.next();
    Assert.assertEquals(true, we.isEnd());
    Assert.assertEquals(4, we.getCommitIdx());

    assertWires(navigation.getUsedWires(0, commits).getUsed());
    assertWires(navigation.getUsedWires(1, commits).getUsed(), 0);
    assertWires(navigation.getUsedWires(2, commits).getUsed(), 0);
    assertWires(navigation.getUsedWires(3, commits).getUsed(), 0);
    assertWires(navigation.getUsedWires(4, commits).getUsed(), 0);

    final Iterator<WireEvent> iterator1 = navigation.createWireEventsIterator(4);
    WireEvent we1 = iterator1.next();
    Assert.assertEquals(true, we1.isEnd());
    Assert.assertEquals(4, we1.getCommitIdx());
  }

  public void testBranchAndMerge() throws Exception {
    final List<CommitHashPlusParents> list = read("1 2\n2 3 4\n3 5\n4 6\n5 7\n6 8\n7 8\n8 9\n9");
    // 4, 4
    final TreeNavigationImpl navigation = new TreeNavigationImpl(2, 2);
    final SkeletonBuilder builder = new SkeletonBuilder(navigation);
    final ReadonlyList.ArrayListWrapper<CommitI> commits = new ReadonlyList.ArrayListWrapper<CommitI>();
    fillData(list, navigation, builder, commits);

    final int[] expectedWireNumbers = {0, 0, 0, 1, 0, 1, 0, 1, 1};
    for (int i = 0; i < 5; i++) {
      final CommitI commitI = (CommitI)commits.get(i);
      // just because of the test data order
      Assert.assertEquals("" + (i + 1), commitI.getHash().getString());
      Assert.assertEquals(expectedWireNumbers[i], commitI.getWireNumber());
    }

    final Iterator<WireEvent> iterator = navigation.createWireEventsIterator(0);
    WireEvent we = iterator.next();
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

    assertWires(navigation.getUsedWires(0, commits).getUsed());
    assertWires(navigation.getUsedWires(1, commits).getUsed(), 0);
    assertWires(navigation.getUsedWires(2, commits).getUsed(), 0, 1);
    assertWires(navigation.getUsedWires(3, commits).getUsed(), 0, 1);
    assertWires(navigation.getUsedWires(4, commits).getUsed(), 0, 1);
    assertWires(navigation.getUsedWires(5, commits).getUsed(), 0, 1);
    assertWires(navigation.getUsedWires(6, commits).getUsed(), 0, 1);
    assertWires(navigation.getUsedWires(7, commits).getUsed(), 0, 1);  // before wires!
    assertWires(navigation.getUsedWires(8, commits).getUsed(), 1);

    final Iterator<WireEvent> iterator1 = navigation.createWireEventsIterator(5);
    WireEvent we1 = iterator1.next();

    Assert.assertEquals(6, we1.getWireEnds()[0]);
    Assert.assertEquals(7, we1.getCommitIdx());
    final int[] commitsEnds1 = we1.getCommitsEnds();
    Assert.assertEquals(2, commitsEnds1.length);
    Assert.assertEquals(5, commitsEnds1[0]);
    Assert.assertEquals(6, commitsEnds1[1]);

    we1 = iterator1.next();
    Assert.assertEquals(true, we1.isEnd());
    Assert.assertEquals(8, we1.getCommitIdx());
  }

  public void testBranchIsMerged() throws Exception {
    final List<CommitHashPlusParents> list = read("1 2 4\n2 3 6\n3 8\n4 5 6\n5 7\n6 9\n7 10\n8 10\n9 10\n 10");
    // 4, 4
    final TreeNavigationImpl navigation = new TreeNavigationImpl(2, 2);
    final SkeletonBuilder builder = new SkeletonBuilder(navigation);
    final ReadonlyList.ArrayListWrapper<CommitI> commits = new ReadonlyList.ArrayListWrapper<CommitI>();
    fillData(list, navigation, builder, commits);

    final int[] expectedWireNumbers = {0, 0, 0, 1, 1, 2, 1, 0, 2, 1, 1};
    for (int i = 0; i < 5; i++) {
      final CommitI commitI = (CommitI)commits.get(i);
      // just because of the test data order
      Assert.assertEquals("" + (i + 1), commitI.getHash().getString());
      Assert.assertEquals(expectedWireNumbers[i], commitI.getWireNumber());
    }

    final Iterator<WireEvent> iterator = navigation.createWireEventsIterator(0);
    WireEvent we = iterator.next();
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

    assertWires(navigation.getUsedWires(0, commits).getUsed());
    assertWires(navigation.getUsedWires(1, commits).getUsed(), 0, 1);
    assertWires(navigation.getUsedWires(2, commits).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(3, commits).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(4, commits).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(5, commits).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(6, commits).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(7, commits).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(8, commits).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(9, commits).getUsed(), 0, 1, 2);

    final Iterator<WireEvent> iterator1 = navigation.createWireEventsIterator(5);
    WireEvent we1 = iterator1.next();
    Assert.assertNull(we1.getWireEnds());
    Assert.assertEquals(5, we1.getCommitIdx());
    int[] commitsEnds1 = we1.getCommitsEnds();
    Assert.assertEquals(2, commitsEnds1.length);
    Assert.assertEquals(1, commitsEnds1[0]);
    Assert.assertEquals(3, commitsEnds1[1]);

    we1 = iterator1.next();
    Assert.assertEquals(7, we1.getWireEnds()[0]);
    Assert.assertEquals(8, we1.getWireEnds()[1]);
    Assert.assertEquals(9, we1.getCommitIdx());
    Assert.assertEquals(true, we1.isEnd());
    commitsEnds1 = we1.getCommitsEnds();
    Assert.assertEquals(3, commitsEnds1.length);
    Assert.assertEquals(6, commitsEnds1[0]);
    Assert.assertEquals(7, commitsEnds1[1]);
    Assert.assertEquals(8, commitsEnds1[2]);
  }

  private List<CommitHashPlusParents> read(final String data) {
    final String[] lines = data.split("\n");
    final List<CommitHashPlusParents> result = new ArrayList<CommitHashPlusParents>(lines.length);
    for (String line : lines) {
      line = line.trim();
      if (StringUtil.isEmptyOrSpaces(line)) continue;
      final int idx = line.indexOf(" ");
      if (idx == -1) {
        result.add(new CommitHashPlusParents(line, ArrayUtil.EMPTY_STRING_ARRAY, 0, ""));
      } else {
        final String[] words = line.substring(idx + 1).split(" ");
        result.add(new CommitHashPlusParents(line.substring(0, idx), words, 0, ""));
      }
    }
    return result;
  }

  private void assertWires(final List<Integer> returned, final Integer... expected) {
    Assert.assertEquals(expected.length, returned.size());
    for (int i = 0; i < returned.size(); i++) {
      final Integer integer = returned.get(i);
      Assert.assertEquals(expected[i], integer);
    }
  }
}
