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

import com.intellij.mock.MockVirtualFileSystem;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.Ring;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.ReadonlyList;
import com.intellij.util.ui.ColumnInfo;
import git4idea.history.wholeTree.*;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

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
      final WireEventI we = iterator.next();
      Assert.assertEquals(true, we.isEnd());
      Assert.assertEquals(true, we.isStart());
      Assert.assertEquals(i, we.getCommitIdx());
    }

    assertWires(navigation.getUsedWires(0, commits, builder.getFutureConvertor()).getUsed());
    assertWires(navigation.getUsedWires(1, commits, builder.getFutureConvertor()).getUsed());
    assertWires(navigation.getUsedWires(2, commits, builder.getFutureConvertor()).getUsed());
    assertWires(navigation.getUsedWires(3, commits, builder.getFutureConvertor()).getUsed());
    assertWires(navigation.getUsedWires(4, commits, builder.getFutureConvertor()).getUsed());

    final Iterator<WireEvent> iterator2 = navigation.createWireEventsIterator(4);
    for (int i = 4; i < 5; i++) {
      final WireEventI we = iterator2.next();
      Assert.assertEquals(true, we.isEnd());
      Assert.assertEquals(true, we.isStart());
      Assert.assertEquals(i, we.getCommitIdx());
    }

    final Iterator<WireEvent> iterator3 = navigation.createWireEventsIterator(2);
    for (int i = 2; i < 5; i++) {
      final WireEventI we = iterator3.next();
      Assert.assertEquals(true, we.isEnd());
      Assert.assertEquals(true, we.isStart());
      Assert.assertEquals(i, we.getCommitIdx());
    }
  }

  private void fillData(List<CommitHashPlusParents> list,
                        TreeNavigationImpl navigation,
                        SkeletonBuilder builder,
                        ReadonlyList.ArrayListWrapper<CommitI> commits) {
    int wasSize = commits.getSize();
    for (CommitHashPlusParents commitHashPlusParents : list) {
      final WireNumberCommitDecoration commitI = createCommit(commitHashPlusParents);
      commits.getDelegate().add(commitI);
      builder.consume(commitI, getParents(commitHashPlusParents), commits, commits.getSize() - 1);
    }
    //navigation.recountWires(wasSize == 0 ? 0 : (wasSize - 1), commits);
    navigation.recalcIndex(commits, builder.getFutureConvertor());
  }

  private WireNumberCommitDecoration createCommit(CommitHashPlusParents commitHashPlusParents) {
    return new WireNumberCommitDecoration(new Commit(commitHashPlusParents.getHash(), commitHashPlusParents.getTime(),
                                              new Ref<Integer>(-1)));
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
    WireEventI we = iterator.next();
    Assert.assertEquals(true, we.isStart());
    Assert.assertEquals(0, we.getCommitIdx());

    we = iterator.next();
    Assert.assertEquals(true, we.isEnd());
    Assert.assertEquals(4, we.getCommitIdx());

    assertWires(navigation.getUsedWires(0, commits, builder.getFutureConvertor()).getUsed());
    assertWires(navigation.getUsedWires(1, commits, builder.getFutureConvertor()).getUsed(), 0);
    assertWires(navigation.getUsedWires(2, commits, builder.getFutureConvertor()).getUsed(), 0);
    assertWires(navigation.getUsedWires(3, commits, builder.getFutureConvertor()).getUsed(), 0);
    assertWires(navigation.getUsedWires(4, commits, builder.getFutureConvertor()).getUsed(), 0);

    final Iterator<WireEvent> iterator1 = navigation.createWireEventsIterator(4);
    WireEventI we1 = iterator1.next();
    Assert.assertEquals(true, we1.isEnd());
    Assert.assertEquals(4, we1.getCommitIdx());
  }

  public void testTwoSteps() throws Exception {
    final List<CommitHashPlusParents> list = read("1 2 3\n2 4\n3 5\n4 6\n5 6\n6 7\n7 8 9");
    final List<CommitHashPlusParents> step2 = read("8 10\n9 11\n10 12\n11 13\n12 14\n13 14\n14 15");
    // 4, 4
    final TreeNavigationImpl navigation = new TreeNavigationImpl(2, 2);
    final SkeletonBuilder builder = new SkeletonBuilder(navigation);
    final ReadonlyList.ArrayListWrapper<CommitI> commits = new ReadonlyList.ArrayListWrapper<CommitI>();
    fillData(list, navigation, builder, commits);

    testFirstStepResults(navigation, commits, builder);
    // end of first step
    fillData(step2, navigation, builder, commits);

    testFirstStepResults(navigation, commits, builder);
    for (int i = 7; i < 14; i++) {
      final CommitI commitI = (CommitI)commits.get(i);
      // just because of the test data order
      Assert.assertEquals("" + (i + 1), commitI.getHash().getString());
    }
    Assert.assertEquals(0, commits.get(7).getWireNumber());
    Assert.assertEquals(1, commits.get(8).getWireNumber());
    Assert.assertEquals(0, commits.get(9).getWireNumber());
    Assert.assertEquals(1, commits.get(10).getWireNumber());
    Assert.assertEquals(0, commits.get(11).getWireNumber());
    Assert.assertEquals(1, commits.get(12).getWireNumber());
    Assert.assertEquals(0, commits.get(13).getWireNumber());

    final Iterator<WireEvent> iterator = navigation.createWireEventsIterator(0);
    testFirstIteratorPart(iterator);
    WireEventI we = iterator.next();

    Assert.assertEquals(6, we.getCommitIdx());
    final int[] commitsStarts = we.getCommitsStarts();
    Assert.assertEquals(2, commitsStarts.length);
    Assert.assertEquals(7, commitsStarts[0]);
    Assert.assertEquals(8, commitsStarts[1]);
    Assert.assertNull(we.getWireEnds());

    we = iterator.next();
    Assert.assertEquals(12, we.getWireEnds()[0]);
    Assert.assertEquals(13, we.getCommitIdx());
    final int[] commitsEnds = we.getCommitsEnds();
    Assert.assertEquals(2, commitsEnds.length);
    Assert.assertEquals(11, commitsEnds[0]);
    Assert.assertEquals(12, commitsEnds[1]);

    assertWires(navigation.getUsedWires(7, commits, builder.getFutureConvertor()).getUsed(),0,1);
    assertWires(navigation.getUsedWires(8, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(9, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(10, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(11, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(12, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(13, commits, builder.getFutureConvertor()).getUsed(), 0,1);
  }

  public void testTwoStepsMinusLastElement() throws Exception {
    final List<CommitHashPlusParents> list = read("1 2 3\n2 4\n3 5\n4 6\n5 6\n6 7\n7 8 9");
    final List<CommitHashPlusParents> step2 = read("8 10\n9 11\n10 12\n11 13\n12 14\n13 14");
    // 4, 4
    final TreeNavigationImpl navigation = new TreeNavigationImpl(2, 2);
    final SkeletonBuilder builder = new SkeletonBuilder(navigation);
    final ReadonlyList.ArrayListWrapper<CommitI> commits = new ReadonlyList.ArrayListWrapper<CommitI>();
    fillData(list, navigation, builder, commits);

    testFirstStepResults(navigation, commits, builder);
    // end of first step
    fillData(step2, navigation, builder, commits);

    testFirstStepResults(navigation, commits, builder);
    for (int i = 7; i < 13; i++) {
      final CommitI commitI = (CommitI)commits.get(i);
      // just because of the test data order
      Assert.assertEquals("" + (i + 1), commitI.getHash().getString());
    }
    Assert.assertEquals(0, commits.get(7).getWireNumber());
    Assert.assertEquals(1, commits.get(8).getWireNumber());
    Assert.assertEquals(0, commits.get(9).getWireNumber());
    Assert.assertEquals(1, commits.get(10).getWireNumber());
    Assert.assertEquals(0, commits.get(11).getWireNumber());
    Assert.assertEquals(1, commits.get(12).getWireNumber());

    final Iterator<WireEvent> iterator = navigation.createWireEventsIterator(0);
    testFirstIteratorPart(iterator);
    WireEventI we = iterator.next();

    Assert.assertEquals(6, we.getCommitIdx());
    final int[] commitsStarts = we.getCommitsStarts();
    Assert.assertEquals(2, commitsStarts.length);
    Assert.assertEquals(7, commitsStarts[0]);
    Assert.assertEquals(8, commitsStarts[1]);
    Assert.assertNull(we.getWireEnds());

    assertWires(navigation.getUsedWires(7, commits, builder.getFutureConvertor()).getUsed(),0,1);
    assertWires(navigation.getUsedWires(8, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(9, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(10, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(11, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(12, commits, builder.getFutureConvertor()).getUsed(), 0,1);
  }

  private void testFirstStepResults(TreeNavigationImpl navigation, ReadonlyList.ArrayListWrapper<CommitI> commits, final SkeletonBuilder builder) {
    for (int i = 0; i < 7; i++) {
      final CommitI commitI = (CommitI)commits.get(i);
      // just because of the test data order
      Assert.assertEquals("" + (i + 1), commitI.getHash().getString());
    }
    Assert.assertEquals(0, commits.get(0).getWireNumber());
    Assert.assertEquals(0, commits.get(1).getWireNumber());
    Assert.assertEquals(1, commits.get(2).getWireNumber());
    Assert.assertEquals(0, commits.get(3).getWireNumber());
    Assert.assertEquals(1, commits.get(4).getWireNumber());
    Assert.assertEquals(0, commits.get(5).getWireNumber());
    Assert.assertEquals(0, commits.get(6).getWireNumber());

    final Iterator<WireEvent> iterator = navigation.createWireEventsIterator(0);
    testFirstIteratorPart(iterator);
    WireEventI we;

    assertWires(navigation.getUsedWires(0, commits, builder.getFutureConvertor()).getUsed());
    assertWires(navigation.getUsedWires(1, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(2, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(3, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(4, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(5, commits, builder.getFutureConvertor()).getUsed(), 0,1);
    assertWires(navigation.getUsedWires(6, commits, builder.getFutureConvertor()).getUsed(), 0);

    final Iterator<WireEvent> iterator1 = navigation.createWireEventsIterator(4);
    we = iterator1.next();
    Assert.assertEquals(4, we.getWireEnds()[0]);
    Assert.assertEquals(5, we.getCommitIdx());
    final int[] commitsEnds1 = we.getCommitsEnds();
    Assert.assertEquals(2, commitsEnds1.length);
    Assert.assertEquals(3, commitsEnds1[0]);
    Assert.assertEquals(4, commitsEnds1[1]);
  }

  private void testFirstIteratorPart(Iterator<WireEvent> iterator) {
    WireEventI we = iterator.next();
    Assert.assertEquals(true, we.isStart());
    Assert.assertEquals(0, we.getCommitIdx());
    final int[] commitsStarts = we.getCommitsStarts();
    Assert.assertEquals(2, commitsStarts.length);
    Assert.assertEquals(1, commitsStarts[0]);
    Assert.assertEquals(2, commitsStarts[1]);
    Assert.assertNull(we.getWireEnds());
    Assert.assertEquals(0, we.getCommitIdx());

    we = iterator.next();
    Assert.assertEquals(4, we.getWireEnds()[0]);
    Assert.assertEquals(5, we.getCommitIdx());
    final int[] commitsEnds = we.getCommitsEnds();
    Assert.assertEquals(2, commitsEnds.length);
    Assert.assertEquals(3, commitsEnds[0]);
    Assert.assertEquals(4, commitsEnds[1]);
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
    WireEventI we = iterator.next();
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
    Arrays.sort(commitsEnds);
    Assert.assertEquals(5, commitsEnds[0]);
    Assert.assertEquals(6, commitsEnds[1]);

    we = iterator.next();
    Assert.assertEquals(true, we.isEnd());
    Assert.assertEquals(8, we.getCommitIdx());

    assertWires(navigation.getUsedWires(0, commits, builder.getFutureConvertor()).getUsed());
    assertWires(navigation.getUsedWires(1, commits, builder.getFutureConvertor()).getUsed(), 0);
    assertWires(navigation.getUsedWires(2, commits, builder.getFutureConvertor()).getUsed(), 0, 1);
    assertWires(navigation.getUsedWires(3, commits, builder.getFutureConvertor()).getUsed(), 0, 1);
    assertWires(navigation.getUsedWires(4, commits, builder.getFutureConvertor()).getUsed(), 0, 1);
    assertWires(navigation.getUsedWires(5, commits, builder.getFutureConvertor()).getUsed(), 0, 1);
    assertWires(navigation.getUsedWires(6, commits, builder.getFutureConvertor()).getUsed(), 0, 1);
    assertWires(navigation.getUsedWires(7, commits, builder.getFutureConvertor()).getUsed(), 0, 1);  // before wires!
    assertWires(navigation.getUsedWires(8, commits, builder.getFutureConvertor()).getUsed(), 1);

    final Iterator<WireEvent> iterator1 = navigation.createWireEventsIterator(5);
    WireEventI we1 = iterator1.next();

    Assert.assertEquals(6, we1.getWireEnds()[0]);
    Assert.assertEquals(7, we1.getCommitIdx());
    final int[] commitsEnds1 = we1.getCommitsEnds();
    Assert.assertEquals(2, commitsEnds1.length);
    Arrays.sort(commitsEnds);
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
    WireEventI we = iterator.next();
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
    Arrays.sort(commitsEnds);
    Assert.assertEquals(6, commitsEnds[0]);
    Assert.assertEquals(7, commitsEnds[1]);
    Assert.assertEquals(8, commitsEnds[2]);

    assertWires(navigation.getUsedWires(0, commits, builder.getFutureConvertor()).getUsed());
    assertWires(navigation.getUsedWires(1, commits, builder.getFutureConvertor()).getUsed(), 0, 1);
    assertWires(navigation.getUsedWires(2, commits, builder.getFutureConvertor()).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(3, commits, builder.getFutureConvertor()).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(4, commits, builder.getFutureConvertor()).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(5, commits, builder.getFutureConvertor()).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(6, commits, builder.getFutureConvertor()).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(7, commits, builder.getFutureConvertor()).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(8, commits, builder.getFutureConvertor()).getUsed(), 0, 1, 2);
    assertWires(navigation.getUsedWires(9, commits, builder.getFutureConvertor()).getUsed(), 0, 1, 2);

    final Iterator<WireEvent> iterator1 = navigation.createWireEventsIterator(5);
    WireEventI we1 = iterator1.next();
    Assert.assertNull(we1.getWireEnds());
    Assert.assertEquals(5, we1.getCommitIdx());
    int[] commitsEnds1 = we1.getCommitsEnds();
    Assert.assertEquals(2, commitsEnds1.length);
    Assert.assertEquals(1, commitsEnds1[0]);
    Assert.assertEquals(3, commitsEnds1[1]);

    we1 = iterator1.next();
    Assert.assertEquals(7, we.getWireEnds()[0]);
    Assert.assertEquals(8, we.getWireEnds()[1]);
    Assert.assertEquals(9, we.getCommitIdx());
    Assert.assertEquals(true, we.isEnd());
    commitsEnds = we.getCommitsEnds();
    Assert.assertEquals(3, commitsEnds.length);
    Arrays.sort(commitsEnds);
    Assert.assertEquals(6, commitsEnds[0]);
    Assert.assertEquals(7, commitsEnds[1]);
    Assert.assertEquals(8, commitsEnds[2]);
  }

  public void testFromMsysgit() throws Exception {
    final List<CommitHashPlusParents> list = read("459 481 485\n481 482 484\n482 483\n483 484\n484 486\n485 486\n486");
    // 4, 4
    final TreeNavigationImpl navigation = new TreeNavigationImpl(2, 2);
    final SkeletonBuilder builder = new SkeletonBuilder(navigation);
    final ReadonlyList.ArrayListWrapper<CommitI> commits = new ReadonlyList.ArrayListWrapper<CommitI>();
    fillData(list, navigation, builder, commits);

    final int[] expectedWireNumbers = {0,0,0,0,2,1,2};
    for (int i = 0; i < 5; i++) {
      final CommitI commitI = (CommitI)commits.get(i);
      // just because of the test data order
      //Assert.assertEquals("" + (i + 1), commitI.getHash().getString());
      Assert.assertEquals(expectedWireNumbers[i], commitI.getWireNumber());
    }

    /*final Iterator<WireEvent> iterator = navigation.createWireEventsIterator(0);
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
    Assert.assertEquals(6, we.getWireEnds()[0]);
    Assert.assertEquals(8, we.getWireEnds()[1]);
    Assert.assertEquals(9, we.getCommitIdx());
    Assert.assertEquals(true, we.isEnd());
    commitsEnds = we.getCommitsEnds();
    Assert.assertEquals(3, commitsEnds.length);
    Arrays.sort(commitsEnds);
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
    Assert.assertEquals(6, we.getWireEnds()[0]);
    Assert.assertEquals(8, we.getWireEnds()[1]);
    Assert.assertEquals(9, we.getCommitIdx());
    Assert.assertEquals(true, we.isEnd());
    commitsEnds = we.getCommitsEnds();
    Assert.assertEquals(3, commitsEnds.length);
    Arrays.sort(commitsEnds);
    Assert.assertEquals(6, commitsEnds[0]);
    Assert.assertEquals(7, commitsEnds[1]);
    Assert.assertEquals(8, commitsEnds[2]);*/
  }
  
  public void testRealMergesAndBranches() throws Exception {
    List<CommitHashPlusParents> list = read("1 2 3\n2 10\n3 4\n4 5 9\n5 6 7\n6 10\n7 8\n8 10\n9 10\n10 11 12\n11 17\n12 13 15\n" +
                                                  "13 14\n14 17\n15 16 17\n16 18\n17 18");
    TreeNavigationImpl navigation = new TreeNavigationImpl(20, 20);
    SkeletonBuilder builder = new SkeletonBuilder(navigation);
    ReadonlyList.ArrayListWrapper<CommitI> commits = new ReadonlyList.ArrayListWrapper<CommitI>();
    fillData(list, navigation, builder, commits);

    testRealResults(navigation, commits);

    List<CommitHashPlusParents> list1 = read("1 2 3\n2 10\n3 4\n4 5 9\n5 6 7\n6 10\n7 8\n8 10\n9 10\n10 11 12\n11 17\n12 13 15\n");
    List<CommitHashPlusParents> list2 = read("13 14\n14 17\n15 16 17\n16 18\n17 18");
    navigation = new TreeNavigationImpl(20, 20);
    builder = new SkeletonBuilder(navigation);
    commits = new ReadonlyList.ArrayListWrapper<CommitI>();
    fillData(list1, navigation, builder, commits);
    fillData(list2, navigation, builder, commits);
    testRealResults(navigation, commits);

    list1 = read("1 2 3\n2 10\n3 4\n4 5 9\n5 6 7\n6 10\n7 8\n8 10\n9 10\n10 11 12\n11 17\n12 13 15\n13 14\n14 17\n");
    list2 = read("15 16 17\n16 18\n17 18");
    navigation = new TreeNavigationImpl(20, 20);
    builder = new SkeletonBuilder(navigation);
    commits = new ReadonlyList.ArrayListWrapper<CommitI>();
    fillData(list1, navigation, builder, commits);
    fillData(list2, navigation, builder, commits);
    testRealResults(navigation, commits);

    list1 = read("1 2 3\n2 10\n3 4\n4 5 9\n5 6 7\n6 10\n7 8\n8 10\n9 10\n10 11 12\n11 17\n12 13 15\n13 14\n14 17\n15 16 17\n");
    list2 = read("16 18\n17 18");
    navigation = new TreeNavigationImpl(20, 20);
    builder = new SkeletonBuilder(navigation);
    commits = new ReadonlyList.ArrayListWrapper<CommitI>();
    fillData(list1, navigation, builder, commits);
    fillData(list2, navigation, builder, commits);
    testRealResults(navigation, commits);
  }

  private void testRealResults(TreeNavigationImpl navigation, ReadonlyList.ArrayListWrapper<CommitI> commits) {
    final int[] expectedWireNumbers = {0,0,1,1,1,   1,3,3,2,0,    0,1,1,1,2,    2,0,0};
    for (int i = 0; i < 5; i++) {
      final CommitI commitI = (CommitI)commits.get(i);
      // just because of the test data order
      Assert.assertEquals("" + (i + 1), commitI.getHash().getString());
      Assert.assertEquals(expectedWireNumbers[i], commitI.getWireNumber());
    }

    Ring<Integer> usedWires = navigation.getUsedWires(17, commits, new Convertor<Integer, List<Integer>>() {
      @Override
      public List<Integer> convert(Integer o) {
        return Collections.emptyList();
      }
    });
    System.out.println("*");
  }

  // todo test events list
  public void testJumpsWithWires() throws Exception {
    final List<CommitHashPlusParents> list = read("1 2 3\n2 3 8\n3 4\n4 5 7\n5 6\n6 7\n7 9\n8 9\n 9");
    // 4, 4
    final TreeNavigationImpl navigation = new TreeNavigationImpl(2, 2);
    final SkeletonBuilder builder = new SkeletonBuilder(navigation);
    final ReadonlyList.ArrayListWrapper<CommitI> commits = new ReadonlyList.ArrayListWrapper<CommitI>();
    fillData(list, navigation, builder, commits);

    final int[] expectedWireNumbers = {0,0,1,1,1,1,2,0,2};
    for (int i = 0; i < 5; i++) {
      final CommitI commitI = (CommitI)commits.get(i);
      // just because of the test data order
      Assert.assertEquals("" + (i + 1), commitI.getHash().getString());
      Assert.assertEquals(expectedWireNumbers[i], commitI.getWireNumber());
    }

    /*final Iterator<WireEvent> iterator = navigation.createWireEventsIterator(0);
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
    Assert.assertEquals(6, we.getWireEnds()[0]);
    Assert.assertEquals(8, we.getWireEnds()[1]);
    Assert.assertEquals(9, we.getCommitIdx());
    Assert.assertEquals(true, we.isEnd());
    commitsEnds = we.getCommitsEnds();
    Assert.assertEquals(3, commitsEnds.length);
    Arrays.sort(commitsEnds);
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
    Assert.assertEquals(6, we.getWireEnds()[0]);
    Assert.assertEquals(8, we.getWireEnds()[1]);
    Assert.assertEquals(9, we.getCommitIdx());
    Assert.assertEquals(true, we.isEnd());
    commitsEnds = we.getCommitsEnds();
    Assert.assertEquals(3, commitsEnds.length);
    Arrays.sort(commitsEnds);
    Assert.assertEquals(6, commitsEnds[0]);
    Assert.assertEquals(7, commitsEnds[1]);
    Assert.assertEquals(8, commitsEnds[2]);*/
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
  
  public void testTwoReposStart() throws Exception {
    final BigTableTableModel model = new BigTableTableModel(Collections.<ColumnInfo>emptyList(), EmptyRunnable.getInstance());
    model.setCommitIdxInterval(2);
    model.setNumEventsInGroup(2);
    final VirtualFile[] arr = new VirtualFile[] {new MyVf(), new MyVf()};
    model.setRootsHolder(new RootsHolder(Arrays.asList(arr)));
    model.clear(true, true);

    final List<List<AbstractHash>> parentsOne = new ArrayList<List<AbstractHash>>();
    final List<List<AbstractHash>> parentsTwo = new ArrayList<List<AbstractHash>>();
    final List<CommitI> one = new ArrayList<CommitI>(2);
    final List<CommitI> two = new ArrayList<CommitI>(2);
    CommitI first = new MultipleRepositoryCommitDecorator(new Commit("1", 1, new Ref<Integer>(-1)), 0);
    one.add(first);

    CommitI second = new MultipleRepositoryCommitDecorator(new Commit("0", 2, new Ref<Integer>(-1)), 1);
    two.add(second);

    parentsOne.add(Collections.singletonList(AbstractHash.create("2")));

    parentsTwo.add(Collections.singletonList(AbstractHash.create("1")));

    model.setStrategy(new CommitGroupingStrategy() {
      @Override
      public String getGroupName(CommitI commit) {
        if ("1".equals(commit.getHash().toString())) return "Header 1";
        return "Header 2";
      }

      @Override
      public void beforeStart() {
      }
    });

    model.appendData(one, parentsOne);
    model.appendData(two, parentsTwo);
    System.out.println("2");
  }

  private static class MyVf extends VirtualFile {
    @NotNull
    @Override
    public String getName() {
      return "mock";
    }

    @NotNull
    @Override
    public VirtualFileSystem getFileSystem() {
      return new MockVirtualFileSystem();
    }

    @Override
    public String getPath() {
      return "mock";
    }

    @NotNull
    @Override
    public String getUrl() {
      return "mock";
    }

    @Override
    public boolean isWritable() {
      return false;
    }

    @Override
    public boolean isDirectory() {
      return false;
    }

    @Override
    public boolean isValid() {
      return false;
    }

    @Override
    public VirtualFile getParent() {
      return null;
    }

    @Override
    public VirtualFile[] getChildren() {
      return new VirtualFile[0];
    }

    @NotNull
    @Override
    public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
      return null;
    }

    @NotNull
    @Override
    public byte[] contentsToByteArray() throws IOException {
      return new byte[0];
    }

    @Override
    public long getTimeStamp() {
      return 0;
    }

    @Override
    public long getLength() {
      return 0;
    }

    @Override
    public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return null;
    }
  }
}