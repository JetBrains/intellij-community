package org.zmlx.hg4idea;

import org.testng.annotations.Test;
import org.zmlx.hg4idea.command.HgCatCommand;

import java.nio.charset.Charset;

import static org.testng.Assert.assertEquals;

public class HgCatTestCase extends AbstractHgTestCase {
  @Test
  public void testCatCurrentRevision() throws Exception {
    fillFile(myProjectRepo, new String[]{"file.txt"}, "initial contents");
    runHgOnProjectRepo("add", ".");
    runHgOnProjectRepo("commit", "-m", "initial contents");

    HgCatCommand command = new HgCatCommand(myProject);
    String content = command.execute(getHgFile("file.txt"), HgRevisionNumber.getLocalInstance("0"), Charset.defaultCharset());
    assertEquals(content, "initial contents");
  }


  @Test
  public void testCatPastRevision() throws Exception {
    fillFile(myProjectRepo, new String[]{"file.txt"}, "initial contents");
    runHgOnProjectRepo("add", ".");
    runHgOnProjectRepo("commit", "-m", "initial contents");

    runHgOnProjectRepo("rename", "file.txt", "file2.txt");
    runHgOnProjectRepo("commit", "-m", "file renamed");

    fillFile(myProjectRepo, new String[]{"file2.txt"}, "updated contents");
    runHgOnProjectRepo("commit", "-m", "updated contents");

    HgCatCommand command = new HgCatCommand(myProject);
    String content = command.execute(getHgFile("file.txt"), HgRevisionNumber.getLocalInstance("0"), Charset.defaultCharset());
    assertEquals(content, "initial contents");
  }


  @Test
  public void testCatTrackFileNames() throws Exception {
    fillFile(myProjectRepo, new String[]{"file.txt"}, "initial contents");
    runHgOnProjectRepo("add", ".");
    runHgOnProjectRepo("commit", "-m", "initial contents");

    runHgOnProjectRepo("rename", "file.txt", "file2.txt");
    runHgOnProjectRepo("commit", "-m", "file renamed");

    fillFile(myProjectRepo, new String[]{"file2.txt"}, "updated contents");
    runHgOnProjectRepo("commit", "-m", "updated contents");

    HgCatCommand command = new HgCatCommand(myProject);
    String content = command.execute(getHgFile("file2.txt"), HgRevisionNumber.getLocalInstance("0"), Charset.defaultCharset());
    assertEquals(content, "initial contents");
  }
}
