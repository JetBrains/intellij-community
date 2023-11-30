// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package hg4idea.test.history;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.provider.HgRepositoryLocation;

import java.time.ZonedDateTime;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.touch;
import static hg4idea.test.HgExecutor.hg;

public class HgBrowseChangesTest extends HgPlatformTest {
  private ChangeBrowserSettings mySettings;
  private String dateBefore;
  private String dateAfter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySettings = new ChangeBrowserSettings();
    cd(myRepository);
    touch("f2.txt");
    hg("add");
    hg("commit -m add");
    ZonedDateTime now = ZonedDateTime.now();
    dateBefore = ChangeBrowserSettings.DATE_FORMAT.format(now.plusYears(1));
    dateAfter = ChangeBrowserSettings.DATE_FORMAT.format(now.withYear(1970));
  }

  public void testLogRevisionWithDataFilter() throws VcsException {
    mySettings.USE_DATE_AFTER_FILTER = true;
    mySettings.USE_DATE_BEFORE_FILTER = true;
    mySettings.DATE_BEFORE = dateBefore;
    mySettings.DATE_AFTER = dateAfter;
    doTest();
  }

  public void testLogRevisionWithAfterDataFilter() throws VcsException {
    mySettings.USE_DATE_AFTER_FILTER = true;
    mySettings.DATE_AFTER = dateAfter;
    doTest();
  }

  public void testLogRevisionWithBeforeDataFilter() throws VcsException {
    mySettings.USE_DATE_BEFORE_FILTER = true;
    mySettings.DATE_BEFORE = dateBefore;
    doTest();
  }

  public void testLogRevisionWithBeforeFilter() throws VcsException {
    mySettings.USE_CHANGE_BEFORE_FILTER = true;
    mySettings.CHANGE_BEFORE = "1";
    doTest();
  }

  public void testLogRevisionWithAfterFilter() throws VcsException {
    mySettings.USE_CHANGE_AFTER_FILTER = true;
    mySettings.CHANGE_AFTER = "0";
    doTest();
  }

  public void testLogRevisionWithFilter() throws VcsException {
    mySettings.USE_CHANGE_BEFORE_FILTER = true;
    mySettings.USE_CHANGE_AFTER_FILTER = true;
    mySettings.USE_DATE_AFTER_FILTER = true;
    mySettings.USE_DATE_BEFORE_FILTER = true;
    mySettings.DATE_BEFORE = dateBefore;
    mySettings.DATE_AFTER = dateAfter;
    mySettings.CHANGE_BEFORE = "1";
    mySettings.CHANGE_AFTER = "0";
    doTest();
  }

  private void doTest() throws VcsException {
    var provider = myVcs.getCommittedChangesProvider();
    assert provider != null;
    @SuppressWarnings("unchecked") var revisions = provider.getCommittedChanges(mySettings, new HgRepositoryLocation(myRepository.getUrl(), myRepository), -1);
    assertFalse(revisions.isEmpty());
  }
}
