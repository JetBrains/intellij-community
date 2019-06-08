// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package hg4idea.test;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.command.HgCommitCommand;
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryImpl;
import org.zmlx.hg4idea.util.HgEncodingUtil;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.echo;

/**
 * @author Nadya Zabrodina
 */
public class HgEncodingTest extends HgPlatformTest {

  //test for  default EncodingProject settings
  public void testCommitUtfMessage() throws HgCommandException, VcsException {
    cd(myRepository);
    echo("file.txt", "lalala");
    HgRepository hgRepo = HgRepositoryImpl.getInstance(myRepository, myProject, myProject);
    HgCommitCommand commitCommand = new HgCommitCommand(myProject, hgRepo, "сообщение");
    commitCommand.executeInCurrentThread();
  }

  //test SpecialCharacters in commit message for default EncodingProject settings
  public void testUtfMessageInHistoryWithSpecialCharacters() throws HgCommandException, VcsException {
    cd(myRepository);
    String fileName = "file.txt";
    echo(fileName, "lalala");
    Charset charset = HgEncodingUtil.getDefaultCharset(myProject);
    String comment = "öäüß";
    HgRepository hgRepo = HgRepositoryImpl.getInstance(myRepository, myProject, myProject);
    HgCommitCommand commitCommand = new HgCommitCommand(myProject, hgRepo, comment);
    commitCommand.executeInCurrentThread();
    HgLogCommand logCommand = new HgLogCommand(myProject);
    myRepository.refresh(false, true);
    VirtualFile file = myRepository.findChild(fileName);
    assert file != null;
    List<HgFileRevision> revisions = logCommand.execute(new HgFile(myProject, file), 1, false);
    HgFileRevision rev = revisions.get(0);
    assertEquals(new String(comment.getBytes(charset), StandardCharsets.UTF_8), rev.getCommitMessage());
  }
}
