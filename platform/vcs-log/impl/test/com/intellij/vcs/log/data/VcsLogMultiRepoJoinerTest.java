package com.intellij.vcs.log.data;

import com.intellij.vcs.log.TimedVcsCommit;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.vcs.log.TimedCommitParser.log;
import static org.junit.Assert.assertEquals;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogMultiRepoJoinerTest {

  @Test
  public void joinTest() {
    List<? extends TimedVcsCommit> first = log("6|-a2|-a0", "3|-a1|-a0", "1|-a0|-");
    List<? extends TimedVcsCommit> second = log("4|-b1|-b0", "2|-b0|-");
    List<? extends TimedVcsCommit> third = log("7|-c1|-c0", "5|-c0|-");

    List<TimedVcsCommit> expected = log("7|-c1|-c0", "6|-a2|-a0", "5|-c0|-", "4|-b1|-b0", "3|-a1|-a0", "2|-b0|-", "1|-a0|-");

    List<? extends TimedVcsCommit> joined = new VcsLogMultiRepoJoiner().join(Arrays.asList(first, second, third));

    assertEquals(expected, joined);
  }

}
