package org.zmlx.hg4idea.test;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.vcsUtil.VcsUtil;
import org.testng.annotations.Test;
import org.zmlx.hg4idea.HgVcs;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * HgHistoryTestCase tests retrieving file history and specific revisions.
 */
public class HgHistoryTestCase extends AbstractHgTestCase {

  /**
   * 1. Make two versions of a file (create, add, commit, modify, commit).
   * 2. Get the revisions history.
   * 3. Verify versions' contents and the current version.
   */
  @Test
  public void testCurrentAndPreviousRevisions() throws Exception {
    int versions = 0;
    fillFile(myProjectRepo, new String[]{ AFILE }, FILE_CONTENT);
    addAll();
    commitAll("initial content");
    versions++;
    fillFile(myProjectRepo, new String[] { AFILE} , FILE_CONTENT_2);
    commitAll("updated content");
    versions++;

    final VcsHistorySession session = getHistorySession(AFILE);
    final List<VcsFileRevision> revisions = session.getRevisionList();
    for (VcsFileRevision rev : revisions) {
      rev.loadContent();
    }

    assertEquals(revisions.size(), versions);
    assertTrue(session.isCurrentRevision(revisions.get(0).getRevisionNumber()));
    assertEquals(revisions.get(0).getContent(), FILE_CONTENT_2.getBytes());
    assertEquals(revisions.get(1).getContent(), FILE_CONTENT.getBytes());
  }

  /**
   * 1. Make initial version of a file (create, add, commit).
   * 2. Rename file (rename, commit).
   * 3. Update file (modify, commit).
   * 4. Get the file history.
   * 5. Verify revision contents and the current revision.
   */
  @Test
  public void renameShouldPreserveFileHistory() throws Exception {
    int versions = 0;

    fillFile(myProjectRepo, new String[]{ AFILE }, FILE_CONTENT);
    addAll();
    commitAll("initial content");
    versions++;

    runHgOnProjectRepo("rename", AFILE, BFILE);
    commitAll("file renamed");
    versions++;

    fillFile(myProjectRepo, new String[]{ BFILE }, FILE_CONTENT_2);
    commitAll("updated content");
    versions++;

    final VcsHistorySession session = getHistorySession(BFILE);
    final List<VcsFileRevision> revisions = session.getRevisionList();
    loadAllRevisions(revisions);

    assertEquals(revisions.size(), versions);
    assertTrue(session.isCurrentRevision(revisions.get(0).getRevisionNumber()));
    assertEquals(revisions.get(0).getContent(), FILE_CONTENT_2.getBytes());
    assertEquals(revisions.get(2).getContent(), FILE_CONTENT.getBytes());
  }

  private void loadAllRevisions(Collection<VcsFileRevision> revisions) throws Exception {
    for (VcsFileRevision rev : revisions) {
      rev.loadContent();
    }
  }

  private VcsHistorySession getHistorySession(String relativePath) throws VcsException {
    return HgVcs.getInstance(myProject).getVcsHistoryProvider().createSessionFor(VcsUtil.getFilePath(new File(myProjectRepo, relativePath), false));
  }

}
