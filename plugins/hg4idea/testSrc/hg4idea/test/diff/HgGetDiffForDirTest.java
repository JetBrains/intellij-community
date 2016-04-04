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
package hg4idea.test.diff;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.vcsUtil.VcsUtil;
import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.util.List;

import static com.intellij.openapi.vcs.Executor.*;
import static hg4idea.test.HgExecutor.hg;

public class HgGetDiffForDirTest extends HgPlatformTest {

  private static final String SHORT_TEMPLATE_REVISION = "{rev}:{node|short}";

  public void testDiffForDir() {
    cd(myRepository);
    touch("A.txt", "dsfdfdsf");
    hg("add A.txt");
    touch("B.txt");
    hg("add B.txt");
    hg("commit -m 2files_added");
    File dirFile = mkdir("dir");
    cd("dir");
    touch("C.txt");
    touch("D.txt");
    hg("add C.txt");
    hg("add D.txt");
    hg("commit -m createDir");
    String[] hash1 = hg("log -l 1 --template=" + SHORT_TEMPLATE_REVISION).split(":");
    HgRevisionNumber r1number = HgRevisionNumber.getInstance(hash1[0], hash1[1]);
    HgFileRevision rev1 =
      new HgFileRevision(myProject, new HgFile(myRepository, dirFile), r1number, "", null, "", "", null, null, null, null);
    echo("C.txt", "aaaa");
    echo("D.txt", "dddd");
    hg("commit -m modifyDir");
    String[] hash2 = hg("log -l 1 --template=" + SHORT_TEMPLATE_REVISION).split(":");
    HgRevisionNumber r2number = HgRevisionNumber.getInstance(hash2[0], hash2[1]);
    HgFileRevision rev2 =
      new HgFileRevision(myProject, new HgFile(myRepository, dirFile), r2number, "", null, "", "", null, null, null, null);
    FilePath dirPath = VcsUtil.getFilePath(dirFile, true);
    List<Change> changes = HgUtil.getDiff(myProject, myRepository, dirPath, rev1.getRevisionNumber(), rev2.getRevisionNumber());
    assertEquals(2, changes.size());
  }
}
