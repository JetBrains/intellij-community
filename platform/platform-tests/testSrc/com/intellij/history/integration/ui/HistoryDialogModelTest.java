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

package com.intellij.history.integration.ui;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IntegrationTestCase;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.ui.models.HistoryDialogModel;
import com.intellij.history.integration.ui.models.RevisionItem;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;

import java.util.List;

public class HistoryDialogModelTest extends IntegrationTestCase {
  HistoryDialogModel m;
  VirtualFile f;

  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    getVcs().beginChangeSet();
    f = createFile("f.txt", "1");
    getVcs().endChangeSet("1");

    getVcs().beginChangeSet();
    setContent(f, "2.txt");
    getVcs().endChangeSet("2");

    getVcs().beginChangeSet();
    setContent(f, "3.txt");
    getVcs().endChangeSet("3");

    initModelFor();
  }

  @Test
  public void testRevisionsList() {
    List<RevisionItem> rr = m.getRevisions();

    assertEquals(3, rr.size());
    assertEquals("3", rr.get(0).revision.getChangeSetName());
    assertEquals("2", rr.get(1).revision.getChangeSetName());
    assertEquals("1", rr.get(2).revision.getChangeSetName());
  }

  @Test
  public void testDoesNotRecomputeRevisionsEveryTime() {
    assertEquals(3, m.getRevisions().size());

    setContent(f, "xxx");
    assertEquals(3, m.getRevisions().size());
  }

  @Test
  public void testRegisteringUnsavedDocumentsBeforeBuildingRevisionsList() {
    setDocumentTextFor(f, "unsaved");
    initModelFor();
    
    m.getRevisions();

    List<Revision> rr = getRevisionsFor(f);
    assertEquals(5, rr.size());
    assertContent("unsaved", rr.get(0).findEntry());
  }

  @Test
  public void testSelectingLastRevisionByDefault() {
    String leftChangeName = "3";
    String rightChangeName = "3";
    assertSelectedRevisins(leftChangeName, rightChangeName);
  }

  @Test
  public void testSelectingOnlyOneRevisionSetsRightToLastOne() {
    m.selectRevisions(0, 0);
    assertSelectedRevisins("3", null);

    m.selectRevisions(1, 1);
    assertSelectedRevisins("2", null);
  }

  @Test
  public void testSelectingTwoRevisions() {
    m.selectRevisions(0, 1);
    assertSelectedRevisins("2", "3");

    m.selectRevisions(1, 2);
    assertSelectedRevisins("1", "2");
  }

  @Test
  public void testClearingSelectionSetsRevisionsToLastOnes() {
    m.selectRevisions(-1, -1);
    assertSelectedRevisins("3", null);
  }

  @Test
  public void testIsCurrentRevisionSelected() {
    m.selectRevisions(1, 2);
    assertFalse(m.isCurrentRevisionSelected());

    m.selectRevisions(2, 2);
    assertTrue(m.isCurrentRevisionSelected());

    m.selectRevisions(-1, -1);
    assertTrue(m.isCurrentRevisionSelected());
  }

  @Test
  public void testIsRevertEnabledForRevision() {
    m.selectRevisions(1, 1);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(1, 2);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(2, 2);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(0, 1);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(0, 0);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(-1, -1);
    assertTrue(m.isRevertEnabled());
  }

  @Test
  public void testIsCreatePatchEnabledForRevision() {
    m.selectRevisions(1, 1);
    assertTrue(m.isCreatePatchEnabled());

    m.selectRevisions(2, 2);
    assertTrue(m.isCreatePatchEnabled());

    m.selectRevisions(1, 2);
    assertTrue(m.isCreatePatchEnabled());

    m.selectRevisions(0, 1);
    assertTrue(m.isRevertEnabled());

    m.selectRevisions(0, 0);
    assertTrue(m.isCreatePatchEnabled());

    m.selectRevisions(-1, -1);
    assertTrue(m.isCreatePatchEnabled());
  }

  private void initModelFor() {
    m = new HistoryDialogModel(myProject, myGateway, getVcs(), f) {
      @Override
      public Reverter createReverter() {
        throw new UnsupportedOperationException();
      }
    };
  }

  private void assertSelectedRevisins(String leftChangeName, String rightChangeName) {
    assertEquals(leftChangeName, m.getLeftRevision().getChangeSetName());
    assertEquals(rightChangeName, m.getRightRevision().getChangeSetName());
  }
}
