/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.provider.HgDiffProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HgDiffProviderTest extends HgSingleUserTest {
  
  @Test
  public void shouldFindCurrentRevisionForRenamedFile() throws Exception{
    
    fillFile(myProjectDir, new String[]{ AFILE }, INITIAL_FILE_CONTENT);
    addAll();
    commitAll("initial content");
    fillFile(myProjectDir, new String[]{AFILE}, UPDATED_FILE_CONTENT);
    commitAll("updated content");

    
    runHgOnProjectRepo("rename", AFILE, BFILE);
    //don't commit 
    
    refreshVfs();
    ChangeListManagerImpl.getInstanceImpl(myProject).ensureUpToDate();
    
    HgDiffProvider diffProvider = new HgDiffProvider(myProject);

    VirtualFile child = myWorkingCopyDir.findChild(BFILE);
    ContentRevision fileContent = diffProvider.createFileContent(diffProvider.getCurrentRevision(child), child);

    assertNotNull(fileContent);
    assertEquals(UPDATED_FILE_CONTENT, fileContent.getContent());
  }
  
  @Test
  public void currentRevisionShouldBeTheRevisionInWhichTheFileLastChanged() throws Exception{
    
    fillFile(myProjectDir, new String[]{AFILE}, INITIAL_FILE_CONTENT);
    addAll();
    commitAll("initial content");
    fillFile(myProjectDir, new String[]{AFILE}, UPDATED_FILE_CONTENT);
    commitAll("updated content");
    
    fillFile(myProjectDir, new String[]{BFILE}, INITIAL_FILE_CONTENT);
    commitAll("added new file");

    
    
    refreshVfs();
    HgDiffProvider diffProvider = new HgDiffProvider(myProject);

    HgRevisionNumber currentRevision = (HgRevisionNumber)diffProvider.getCurrentRevision(myWorkingCopyDir.findChild(AFILE));
    assertNotNull("The current revision for AFILE should be found", currentRevision);
    assertEquals("The diff provider should return the revision in which AFILE was last changed", "1", currentRevision.getRevision());
  }
}
