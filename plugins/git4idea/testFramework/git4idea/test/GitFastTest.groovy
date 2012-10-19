/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.test

import com.intellij.dvcs.test.DvcsTestPlatformFacade
import com.intellij.dvcs.test.MockVcsHelper
import com.intellij.dvcs.test.MockVirtualFile
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.junit.After
import org.junit.Before

import static junit.framework.Assert.assertEquals
import static junit.framework.Assert.assertNotNull

/**
 * 
 * @author Kirill Likhodedov
 * @deprecated Use {@link GitLightTest}
 */
@Deprecated
class GitFastTest {

  public static final String TEST_NOTIFICATION_GROUP = "Test"

  Project myProject
  DvcsTestPlatformFacade myPlatformFacade
  GitTestRepositoryManager myRepositoryManager
  MockGit myGit
  String myProjectDir
  TestDialogManager myDialogManager
  MockVcsHelper myVcsHelper

  @Before
  void setUp() {
    myProjectDir = FileUtil.createTempDirectory("git", null)

    myProject = [
            getBaseDir: { new MockVirtualFile(myProjectDir) }
    ] as Project

    myPlatformFacade = new GitTestPlatformFacade()
    myGit = new MockGit()
    myDialogManager = myPlatformFacade.getDialogManager()
    myRepositoryManager = (GitTestRepositoryManager) myPlatformFacade.getRepositoryManager(myProject)
    myVcsHelper = (MockVcsHelper) myPlatformFacade.getVcsHelper(myProject)
  }

  @After
  void tearDown() {
    FileUtil.delete(new File(myProjectDir))
  }

  void assertNotificationShown(Notification expected) {
    if (expected) {
      Notification actualNotification = (myPlatformFacade.getNotificator(myProject) as TestNotificator).lastNotification
      assertNotNull "No notification was shown", actualNotification
      assertEquals "Notification has wrong title", expected.title, actualNotification.title
      assertEquals "Notification has wrong type", expected.type, actualNotification.type
      assertEquals "Notification has wrong content", adjustTestContent(expected.content), actualNotification.content
    }
  }

  // we allow more spaces and line breaks in tests to make them more readable.
  // After all, notifications display html, so all line breaks and extra spaces are ignored.
  String adjustTestContent(String s) {
    StringBuilder res = new StringBuilder()
    s.split("\n").each { res.append it.trim() }
    res.toString()
  }

  void assertNotificationShown(String title, String message, NotificationType type) {
    assertNotificationShown(new Notification(TEST_NOTIFICATION_GROUP, title, message, type))
  }

}
