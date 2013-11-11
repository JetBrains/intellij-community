package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.VcsRefImpl;
import com.intellij.vcs.log.SimpleHash;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.awt.*;
import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogJoinerTest {

  public void runTest(List<String> initial, List<String> updateBlock, List<String> oldRefs, List<String> newRefs, String expected) {
    List<TimedVcsCommit> savedLog = TimedCommitParser.log(ArrayUtil.toStringArray(initial));
    List<? extends TimedVcsCommit> firstBlock = TimedCommitParser.log(ArrayUtil.toStringArray(updateBlock));
    Collection<VcsRef> vcsOldRefs = ContainerUtil.map(oldRefs, new Function<String, VcsRef>() {
      @Override
      public VcsRef fun(String s) {
        return ref(s, s);
      }
    });
    Collection<VcsRef> vcsNewRefs = ContainerUtil.map(newRefs, new Function<String, VcsRef>() {
      @Override
      public VcsRef fun(String s) {
        return ref(s, s);
      }
    });

    List<? extends TimedVcsCommit> result = new VcsLogJoiner().addCommits(savedLog, vcsOldRefs, firstBlock, vcsNewRefs).getFirst();
    assertEquals(expected, toStr(result));
  }

  @Test
  public void simpleTest() {
    runTest(
      asList("4|-a2|-a1", "3|-b1|-a", "2|-a1|-a", "1|-a|-"),
      asList("5|-f|-b1", "6|-e|-a2"),
      asList("a2", "b1"),
      asList("f", "e"),
      "e, f, a2, b1, a1, a"
    );
  }

  @Test
  public void simpleRemoveCommitsTest() {
    runTest(
      asList("4|-a2|-a1", "3|-b1|-a", "2|-a1|-a", "1|-a|-"),
      asList("5|-f|-b1", "6|-e|-a1"),
      asList("a2"),
      asList("f", "e"),
      "e, f, b1, a1, a"
    );
  }

  @Test
  public void removeCommitsTest() {
    runTest(
      asList("5|-a5|-a4", "4|-a4|-a2 a3", "3|-a3|-a1", "2|-a2|-a1", "1|-a1|-"),
      asList("6|-a6|-a3"),
      asList("a5"),
      asList("a6"),
      "a6, a3, a1"
    );
  }

  @Test
  public void removeCommitsTest2() {
    runTest(
      asList("2|-a2|-a1", "1|-a1|-"),
      asList("5|-a5|-a4", "3|-a3|-a2", "4|-a4|-a3"),
      asList("a2"),
      asList("a5"),
      "a5, a4, a3, a2, a1"
    );
  }

  @Test
  public void removeCommitsTest3() {
    runTest(
      asList("3|-a3|-a2", "2|-a2|-a1", "1|-a1|-"),
      asList("2|-a2|-a1"),
      asList("a3"),
      asList("a2"),
      "a2, a1"
    );
  }

  private static String toStr(List<? extends TimedVcsCommit> commits) {
    StringBuilder s = new StringBuilder();
    for (TimedVcsCommit commit : commits) {
      if (s.length() != 0) {
        s.append(", ");
      }
      s.append(commit.getHash().asString());
    }
    return s.toString();
  }

  private static VcsRef ref(String name, String hash) {
    return new VcsRefImpl(new NotNullFunction<Hash, Integer>() {
      @NotNull
      @Override
      public Integer fun(Hash hash) {
        return Integer.parseInt(hash.asString().substring(0, Math.min(4, hash.asString().length())), 16);
      }
    }, new SimpleHash(hash), name, new VcsRefType() {
      @Override
      public boolean isBranch() {
        return true;
      }

      @NotNull
      @Override
      public Color getBackgroundColor() {
        return Color.WHITE;
      }
    }, new StubVirtualFile());
  }
}
