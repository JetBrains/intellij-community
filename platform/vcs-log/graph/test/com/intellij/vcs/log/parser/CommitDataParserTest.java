package com.intellij.vcs.log.parser;

import com.intellij.vcs.log.VcsShortCommitDetails;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class CommitDataParserTest {

  private static String toStr(@NotNull VcsShortCommitDetails commitData) {
    StringBuilder s = new StringBuilder();
    s.append(commitData.getHash()).append("|-");
    s.append(commitData.getAuthorName()).append("|-");
    s.append(commitData.getAuthorTime()).append("|-");
    s.append(commitData.getSubject());
    return s.toString();
  }

  private void runTest(@NotNull String inputStr) {
    VcsShortCommitDetails commitData = CommitParser.parseCommitData(inputStr);
    assertEquals(inputStr, toStr(commitData));
  }

  @Test
  public void simple1() {
    runTest("af56|-author|-1435|-message");
  }

  @Test
  public void emptyMessage() {
    runTest("12|-author|-1435|-");
  }

  @Test
  public void longAuthor() {
    runTest("af56|-author  skdhfb  j 2353246|-1435|-message");
  }

  @Test
  public void strangeMessage() {
    runTest("af56|-author |-1435|-m|-|-es dfsage");
  }

  @Test
  public void bigTimestamp() {
    runTest("af56|-author |-143523623|-m|-|-es dfsage");
  }

  @Test
  public void emptyAuthor() {
    runTest("af56|-|-143523623|-");
  }

  @Test
  public void emptyTimestamp() {
    VcsShortCommitDetails commitData = CommitParser.parseCommitData("af56|-author |-|-message");
    Assert.assertEquals("author ", commitData.getAuthorName());
    Assert.assertEquals(0, commitData.getAuthorTime());
    Assert.assertEquals("message", commitData.getSubject());
  }

}
