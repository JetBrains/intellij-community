package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.impl.VcsRefImpl;
import com.intellij.vcs.log.VcsRefType;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.parser.CommitParser;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogJoinerTest {

  @Test
  public void simpleTest() {
    String[] INITIAL = {"4|-a2|-a1", "3|-b1|-a", "2|-a1|-a", "1|-a|-"};
    List<TimedVcsCommit> fullLog = CommitParser.log(INITIAL);
    List<? extends TimedVcsCommit> firstBlock = CommitParser.log("5|-f|-b1", "6|-e|-a2");
    Collection<VcsRef> refs = Arrays.asList(ref("master", "e"), ref("release", "f"));

    List<TimedVcsCommit> expected = CommitParser.log(ArrayUtil.mergeArrays(new String[]{"6|-e|-a2", "5|-f|-b1"}, INITIAL));

    List<? extends TimedVcsCommit> result = new VcsLogJoiner().addCommits(fullLog, refs, firstBlock, refs).getFirst();

    assertEquals(expected, result);
  }

  private static VcsRef ref(String name, String hash) {
    return new VcsRefImpl(HashImpl.build(hash), name, new VcsRefType() {
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
