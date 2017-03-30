/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.shelf;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jdom.Element;

import java.io.File;
import java.util.Calendar;
import java.util.Date;

import static com.intellij.openapi.vcs.Executor.debug;

public class UnshelvedChangelistsCleaningTest extends PlatformTestCase {

  private Calendar myCalendar;
  private int TEST_YEAR;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myCalendar = Calendar.getInstance();
    TEST_YEAR = 2000;
    myCalendar.set(TEST_YEAR, Calendar.JANUARY, 1);
  }

  public void testDefaultCleaningWhenProjectOpened() throws Exception {
    myCalendar.add(Calendar.DAY_OF_MONTH, -7);
    doTest();
  }

  public void testDefaultCleaningNothingToDelete() throws Exception {
    myCalendar.add(Calendar.DAY_OF_MONTH, -1);
    doTest();
  }

  public void testCleaningWeek() throws Exception {
    myCalendar.add(Calendar.DAY_OF_MONTH, -7);
    doTest();
  }

  public void testCleaningMonth() throws Exception {
    myCalendar.add(Calendar.MONTH, -1);
    doTest();
  }

  public void testCleaningYear() throws Exception {
    myCalendar.add(Calendar.YEAR, -1);
    doTest();
  }

  private void doTest() throws Exception {
    String testDataPath = VcsTestUtil.getTestDataPath() + "/shelf/" + getTestName(true);
    File beforeFile = new File(testDataPath, "before");
    File afterFile = new File(testDataPath, "after");
    VirtualFile afterDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(afterFile);
    assertNotNull(afterDir);
    File shelfFile = new File(myProject.getBasePath(), ".shelf");
    FileUtil.createDirectory(shelfFile);
    myFilesToDelete.add(shelfFile);
    FileUtil.copyDir(beforeFile, shelfFile);
    VirtualFile shelfDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(shelfFile);
    assertNotNull(shelfDir);
    File beforeXmlInfo = new File(testDataPath, "before.xml");
    assert (beforeXmlInfo.exists());
    Element element = JDOMUtil.load(beforeXmlInfo);
    ShelveChangesManager shelveChangesManager = ShelveChangesManager.getInstance(myProject);
    shelveChangesManager.readExternal(element);
    shelfDir.refresh(false, true);

    assertFalse(shelveChangesManager.getRecycledShelvedChangeLists().isEmpty());
    Date calendarTime = myCalendar.getTime();
    String datePresentation = DateFormatUtil.formatDate(calendarTime);
    assertTrue("Calendar date is: " + datePresentation, myCalendar.get(Calendar.YEAR) < TEST_YEAR);
    debug(datePresentation);
    shelveChangesManager.cleanUnshelved(false, myCalendar.getTimeInMillis());
    PlatformTestUtil.saveProject(myProject);
    
    assertFalse(shelveChangesManager.getRecycledShelvedChangeLists().isEmpty());
    shelfDir.refresh(false, true);
    afterDir.refresh(false, true);
    PlatformTestUtil.assertDirectoriesEqual(afterDir, shelfDir);
  }
}
