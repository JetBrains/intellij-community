package com.intellij.vcs.log.parser;

import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.TimeCommitParents;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class TimestampCommitParentsParserTest {

  private String toStr(TimeCommitParents commitParents) {
    StringBuilder s = new StringBuilder();
    s.append(commitParents.getAuthorTime()).append("|-");
    s.append(commitParents.getHash().toStrHash()).append("|-");
    for (int i = 0; i < commitParents.getParents().size(); i++) {
      Hash hash = commitParents.getParents().get(i);
      if (i != 0) {
        s.append(" ");
      }
      s.append(hash.toStrHash());
    }
    return s.toString();
  }


  private void runTest(String inputStr) {
    TimeCommitParents commitParents = CommitParser.parseTimestampParentHashes(inputStr);
    assertEquals(inputStr, toStr(commitParents));
  }

  @Test
  public void simple() {
    runTest("1|-af|-");
  }

  @Test
  public void parents() {
    runTest("12314|-af|-12 fd");
  }


  @Test
  public void parent() {
    runTest("12314|-af|-12");
  }


  @Test
  public void longTest() {
    runTest("123142412423412|-af|-12");
  }
}
