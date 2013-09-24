package com.intellij.vcs.log.parser;

import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.CommitParents;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class CommitParserTest {
  private String toStr(CommitParents commitParentHashes) {
    StringBuilder s = new StringBuilder();
    s.append(commitParentHashes.getHash().asString()).append("|-");
    for (int i = 0; i < commitParentHashes.getParents().size(); i++) {
      Hash hash = commitParentHashes.getParents().get(i);
      if (i != 0) {
        s.append(" ");
      }
      s.append(hash.asString());
    }
    return s.toString();
  }

  private void runTest(String inputStr) {
    CommitParents commitParents = CommitParser.parseCommitParents(inputStr);
    assertEquals(inputStr, toStr(commitParents));
  }

  @Test
  public void simple1() {
    runTest("a312|-");
  }

  @Test
  public void parent() {
    runTest("a312|-23");
  }

  @Test
  public void twoParent() {
    runTest("a312|-23 a54");
  }


  @Test
  public void moreParent() {
    runTest("a312|-23 a54 abcdef34 034f 00af 00000");
  }


}
