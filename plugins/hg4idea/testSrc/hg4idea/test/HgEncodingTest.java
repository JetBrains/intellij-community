/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    commitCommand.execute();
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
    commitCommand.execute();
    HgLogCommand logCommand = new HgLogCommand(myProject);
    myRepository.refresh(false, true);
    VirtualFile file = myRepository.findChild(fileName);
    assert file != null;
    List<HgFileRevision> revisions = logCommand.execute(new HgFile(myProject, file), 1, false);
    HgFileRevision rev = revisions.get(0);
    assertEquals(new String(comment.getBytes(charset)), rev.getCommitMessage());
  }
}
