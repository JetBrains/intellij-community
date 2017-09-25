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
package hg4idea.test.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.provider.HgHistoryProvider;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.util.List;

import static com.intellij.openapi.vcs.Executor.*;
import static hg4idea.test.HgExecutor.hg;

public class HgHistoryTest extends HgPlatformTest {
  static final String[] names = {"f1.txt", "f2.txt", "f3.txt"};
  static final String subDirName = "sub";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    cd(myRepository);
    appendToHgrc(myRepository, "[extensions]\n" +
                                "largefiles=!\n");
    mkdir(subDirName);
    cd(subDirName);
    touch(names[0], "f1");
    myRepository.refresh(false, true);
    hg("add " + names[0]);
    hg("commit -m a");

    for (int i = 1; i < names.length; ++i) {
      hg("mv " + names[i - 1] + " " + names[i]);
      myRepository.refresh(false, true);
      echo(names[i], "f" + i);
      hg("commit -m a ");
    }
  }

  public void testFileNameInTargetRevisionAfterRename() throws HgCommandException {
    cd(myRepository);
    int namesSize = names.length;
    VirtualFile subDir = myRepository.findFileByRelativePath(subDirName);
    assert subDir != null;
    VirtualFile vFile = VfsUtil.findFileByIoFile(new File(subDir.getPath(),names[namesSize - 1]), true);
    assert vFile != null;
    HgFile hgFile = new HgFile(myRepository, VfsUtilCore.virtualToIoFile(vFile));
    HgLogCommand logCommand = new HgLogCommand(myProject);
    logCommand.setFollowCopies(true);
    List<HgFileRevision> revisions = logCommand.execute(hgFile, -1, true);
    for (int i = 0; i < revisions.size(); ++i) {
      HgFile expectedFile = new HgFile(myRepository, new File(subDir.getPath(), names[namesSize - i - 1]));
      HgFile targetFileName = HgUtil.getFileNameInTargetRevision(myProject, revisions.get(i).getRevisionNumber(), hgFile);
      assertEquals(expectedFile.getRelativePath(),
                   targetFileName.getRelativePath());
    }
  }

  public void testFileNameInTargetRevisionAfterUpdate() throws HgCommandException {
    cd(myRepository);
    //update to parent revision
    hg("update -r .^");
    //update filenames size which is in use
    int namesSize = names.length - 1;
    //find file with parent revision name
    VirtualFile subDir = myRepository.findFileByRelativePath(subDirName);
    assert subDir != null;
    VirtualFile vFile = VfsUtil.findFileByIoFile(new File(subDir.getPath(),names[namesSize - 1]), true);
    assert vFile != null;
    HgFile hgFile = new HgFile(myRepository, VfsUtilCore.virtualToIoFile(vFile));
    HgLogCommand logCommand = new HgLogCommand(myProject);
    logCommand.setFollowCopies(true);
    List<HgFileRevision> revisions = logCommand.execute(hgFile, -1, true);
    for (int i = 0; i < revisions.size(); ++i) {
      HgFile expectedFile = new HgFile(myRepository, new File(subDir.getPath(), names[namesSize - i - 1]));
      HgFile targetFileName = HgUtil.getFileNameInTargetRevision(myProject, revisions.get(i).getRevisionNumber(), hgFile);
      assertEquals(expectedFile.getRelativePath(),
                   targetFileName.getRelativePath());
    }
  }

  public void testFileNameInTargetRevisionFromAffectedFiles() throws HgCommandException {
    cd(myRepository);
    int namesSize = names.length;
    VirtualFile subDir = myRepository.findFileByRelativePath(subDirName);
    assert subDir != null;
    VirtualFile vFile = VfsUtil.findFileByIoFile(new File(subDir.getPath(), names[namesSize - 1]), true);
    assert vFile != null;
    HgFile localFile = new HgFile(myRepository, VfsUtilCore.virtualToIoFile(vFile));
    HgLogCommand logCommand = new HgLogCommand(myProject);
    logCommand.setFollowCopies(true);
    List<HgFileRevision> revisions = logCommand.execute(localFile, -1, true);
    for (int i = 0; i < namesSize; ++i) {
      HgFile hgFile = new HgFile(myRepository, new File(subDir.getPath(), names[namesSize - i - 1]));
      HgFile targetFileName = HgUtil.getFileNameInTargetRevision(myProject, revisions.get(i).getRevisionNumber(), hgFile);
      assertEquals(hgFile.getRelativePath(),
                   targetFileName.getRelativePath());
    }
  }

  public void testUncommittedRenamedFileHistory() {
    cd(myRepository);
    VirtualFile subDir = myRepository.findFileByRelativePath(subDirName);
    assert subDir != null;
    cd(subDir);
    int namesSize = names.length;
    String beforeName = names[namesSize - 1];
    VirtualFile before = VfsUtil.findFileByIoFile(new File(subDir.getPath(), beforeName), true);
    assert before != null;
    FilePath filePath = VcsUtil.getFilePath(VfsUtilCore.virtualToIoFile(before));
    final String renamed = "renamed";
    hg("mv " + beforeName + " " + renamed);
    myRepository.refresh(false, true);
    List<HgFileRevision> revisions = HgHistoryProvider.getHistory((filePath), myRepository, myProject);
    assertEquals(3, revisions.size());
  }
}
