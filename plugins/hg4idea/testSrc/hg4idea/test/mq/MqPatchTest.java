/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package hg4idea.test.mq;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.TimedVcsCommit;
import hg4idea.test.HgPlatformTest;
import hg4idea.test.HgTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.command.mq.HgQImportCommand;
import org.zmlx.hg4idea.command.mq.HgQNewCommand;
import org.zmlx.hg4idea.log.HgHistoryUtil;
import org.zmlx.hg4idea.mq.HgMqAdditionalPatchReader;
import org.zmlx.hg4idea.mq.MqPatchDetails;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.intellij.openapi.vcs.Executor.*;
import static hg4idea.test.HgExecutor.hg;

public class MqPatchTest extends HgPlatformTest {

  private static final String SUBJECT = "mqCommand executed!";
  private static final String MESSAGE = SUBJECT + "\nDont worry!";
  private static final String FILENAME = "f1.txt";
  private static final String BRANCH = "abranch";
  private HgRepository myHgRepository;
  private VirtualFile myMqPatchDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    cd(myRepository);
    appendToHgrc(myRepository, "[extensions]\n" +
                                "mq=\n");
    HgTestUtil.updateDirectoryMappings(myProject, myRepository);
    updateRepoConfig(myProject, myRepository);

    hg("qinit");
    touch(FILENAME, "f1");
    hg("branch " + BRANCH);
    myRepository.refresh(false, true);
    hg("add " + FILENAME);
    hg("commit -m \'" + MESSAGE + "\'");
    HgTestUtil.updateDirectoryMappings(myProject, myRepository);
    myHgRepository = HgUtil.getRepositoryManager(myProject).getRepositoryForRoot(myRepository);
    assert myHgRepository != null;
    myMqPatchDir = myHgRepository.getHgDir().findChild("patches");
  }

  public void testMqPatchInfoAfterQImport() throws Exception {
    cd(myRepository);
    HgQImportCommand importCommand = new HgQImportCommand(myHgRepository);
    importCommand.execute("tip");
    MqPatchDetails patchDetails = updateAndGetDetails();
    TimedVcsCommit tipCommitDetailsFromLog = getLastRevisionDetails();
    assertEqualsCommitInfo(tipCommitDetailsFromLog, patchDetails);
  }

  public void testMqPatchInfoAfterQNew() throws Exception {
    cd(myRepository);
    append(FILENAME, "modify");
    new HgQNewCommand(myProject, myHgRepository, MESSAGE, false).execute();
    MqPatchDetails patchDetails = updateAndGetDetails();
    assertEqualsCommitInfo(null, patchDetails);
  }

  @NotNull
  private MqPatchDetails updateAndGetDetails() {
    myHgRepository.update();
    List<HgNameWithHashInfo> appliedPatches = myHgRepository.getMQAppliedPatches();
    assertEquals(1, appliedPatches.size());
    String patchName = ContainerUtil.getFirstItem(HgUtil.getNamesWithoutHashes(appliedPatches));
    assertNotNull(patchName);
    return HgMqAdditionalPatchReader.readMqPatchInfo(myRepository, getFileByPatchName(patchName));
  }

  private TimedVcsCommit getLastRevisionDetails() throws VcsException {
    //noinspection unchecked
    return (TimedVcsCommit)ContainerUtil.getFirstItem(HgHistoryUtil.readAllHashes(myProject, myRepository, Consumer.EMPTY_CONSUMER,
                                                                  Arrays.asList("-r", "tip")));
  }

  private File getFileByPatchName(@NotNull String patchName) {
    return new File(myMqPatchDir.getPath(), patchName);
  }

  private void assertEqualsCommitInfo(@Nullable TimedVcsCommit logCommit, MqPatchDetails details) {
    if (logCommit != null) {
      List<Hash> parents = logCommit.getParents();
      assertEquals(logCommit.getId().asString(), details.getNodeId());
      assertEquals(!ContainerUtil.isEmpty(parents) ? parents.get(0).asString() : null, details.getParent());
      assertEquals(new Date(logCommit.getTimestamp()), details.getDate());
      assertEquals(BRANCH, details.getBranch());
    }
    assertEquals(MESSAGE, details.getMessage());
    assertEquals(myHgRepository.getRepositoryConfig().getNamedConfig("ui", "username"), details.getUser());
  }
}
