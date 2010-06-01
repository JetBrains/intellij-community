package org.zmlx.hg4idea;

import org.testng.annotations.*;
import org.zmlx.hg4idea.command.*;

import java.nio.charset.*;

import static org.testng.Assert.*;

public class HgRevertTestCase extends HgTestCase {
  @Test
  public void testRevertToCurrentRevision() throws Exception {
    fillFile(projectRepo, new String[]{"file.txt"}, "initial contents");
    runHgOnProjectRepo("add", ".");
    runHgOnProjectRepo("commit", "-m", "initial contents");

    fillFile(projectRepo, new String[]{"file.txt"}, "new contents");

    HgRevertCommand revertCommand = new HgRevertCommand(myProject);
    revertCommand.execute(getHgFile("file.txt"), null, false);

    HgCatCommand catCommand = new HgCatCommand(myProject);
    String content = catCommand.execute(getHgFile("file.txt"), null, Charset.defaultCharset());

    assertEquals(content, "initial contents");
  }


  @Test
  public void testRevertToGivenRevision() throws Exception {
    fillFile(projectRepo, new String[]{"file.txt"}, "initial contents");
    runHgOnProjectRepo("add", ".");
    runHgOnProjectRepo("commit", "-m", "initial contents");

    fillFile(projectRepo, new String[]{"file.txt"}, "new contents");
    runHgOnProjectRepo("commit", "-m", "new contents");

    HgRevertCommand revertCommand = new HgRevertCommand(myProject);
    revertCommand.execute(getHgFile("file.txt"), HgRevisionNumber.getLocalInstance("0"), false);

    HgCatCommand catCommand = new HgCatCommand(myProject);
    String content = catCommand.execute(getHgFile("file.txt"), HgRevisionNumber.getLocalInstance("0"), Charset.defaultCharset());

    assertEquals(content, "initial contents");
  }

}
