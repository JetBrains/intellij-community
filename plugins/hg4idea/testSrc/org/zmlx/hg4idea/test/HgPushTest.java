/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.zmlx.hg4idea.test;

import com.intellij.openapi.vfs.VirtualFile;
import org.testng.annotations.Test;
import org.zmlx.hg4idea.command.HgPushCommand;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;

import static org.testng.Assert.assertNotNull;

/**
 * @author Kirill Likhodedov
 */
public class HgPushTest extends HgCollaborativeTest {

  /**
   * Tests 'push' using only native commands.
   * This is to test the harness.
   */
  @Test
  public void testNativeCommands() throws Exception {
    createFileInCommand(AFILE, "initial content");
    myRepo.add();
    myRepo.commit();
    myRepo.push();

    myParentRepo.update();
    assertNotNull(myParentRepo.getDir().findChild(AFILE));
  }

  /**
   * Testing HgPushCommand:
   * 1. Create a file, add to the VCS and commit via ChangeListManager.
   * 2. Push via HgPushCommand.
   * 3. Natively update parent repository.
   * 4. Verify that the changes appeared there.
   */
  @Test
  public void testHgPushCommand() throws Exception {
    final VirtualFile vf = createFileInCommand(AFILE, "initial content");
    myChangeListManager.addUnversionedFilesToVcs(vf);
    myChangeListManager.checkFilesAreInList(true, vf);
    myChangeListManager.commitFiles(vf);

    final HgPushCommand command = new HgPushCommand(myProject, myRepo.getDir(), myParentRepo.getDir().getUrl());
    command.execute(HgCommandResultHandler.DUMB);

    myParentRepo.update();
    assertNotNull(myParentRepo.getDir().findChild(AFILE));
  }
}
