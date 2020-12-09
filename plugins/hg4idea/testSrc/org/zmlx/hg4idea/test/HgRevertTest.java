package org.zmlx.hg4idea.test;

import org.junit.Test;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.command.HgCatCommand;
import org.zmlx.hg4idea.command.HgRevertCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HgRevertTest extends HgSingleUserTest {
  @Test
  public void testRevertToCurrentRevision() throws Exception {
    fillFile(myProjectDir, new String[]{"file.txt"}, "initial contents");
    runHgOnProjectRepo("add", ".");
    runHgOnProjectRepo("commit", "-m", "initial contents");

    fillFile(myProjectDir, new String[]{"file.txt"}, "new contents");

    HgRevertCommand revertCommand = new HgRevertCommand(myProject);
    revertCommand.execute(myRepo.getDir(), Collections.singleton(new File(myProjectDir, "file.txt").getPath()), null, false);

    HgCatCommand catCommand = new HgCatCommand(myProject);
    HgCommandResult result = catCommand.execute(getHgFile("file.txt"), null, Charset.defaultCharset());
    assertNotNull(result);
    assertEquals("Wrong cat output: " + result.getRawOutput() + "with error:" + result.getRawError(), "initial contents",
                 new String(result.getBytesOutput(), StandardCharsets.UTF_8));
  }


  @Test
  public void testRevertToGivenRevision() throws Exception {
    fillFile(myProjectDir, new String[]{"file.txt"}, "initial contents");
    runHgOnProjectRepo("add", ".");
    runHgOnProjectRepo("commit", "-m", "initial contents");

    fillFile(myProjectDir, new String[]{"file.txt"}, "new contents");
    runHgOnProjectRepo("commit", "-m", "new contents");

    HgRevertCommand revertCommand = new HgRevertCommand(myProject);
    revertCommand.execute(myRepo.getDir(), Collections.singleton(new File(myProjectDir, "file.txt").getPath()),
                          HgRevisionNumber.getLocalInstance("0"), false);

    HgCatCommand catCommand = new HgCatCommand(myProject);
    HgCommandResult result = catCommand.execute(getHgFile("file.txt"), HgRevisionNumber.getLocalInstance("0"), Charset.defaultCharset());
    assertNotNull(result);
    assertEquals("Wrong cat output: " + result.getRawOutput() + "with error:" + result.getRawError(), "initial contents",
                 new String(result.getBytesOutput(), StandardCharsets.UTF_8));
  }
}
