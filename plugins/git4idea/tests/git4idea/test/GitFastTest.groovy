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

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import git4idea.tests.TestDialogManager
import org.junit.Before

import static git4idea.test.GitGTestUtil.stripLineBreaksAndHtml
import static junit.framework.Assert.assertEquals
import static junit.framework.Assert.assertNotNull

/**
 * 
 * @author Kirill Likhodedov
 */
class GitFastTest {

  public static final String TEST_NOTIFICATION_GROUP = "Test"

  Project myProject
  GitTestPlatformFacade myPlatformFacade
  GitTestRepositoryManager myRepositoryManager
  MockGit myGit
  String myProjectDir
  TestDialogManager myDialogManager
  MockVcsHelper myVcsHelper

  @Before
  void setUp() {
    myProjectDir = FileUtil.createTempDirectory("git", null)

    myProject = [
            getBaseDir: { new GitMockVirtualFile(myProjectDir) }
    ] as Project

    myPlatformFacade = new GitTestPlatformFacade()
    myGit = new MockGit()
    myDialogManager = myPlatformFacade.getDialogManager()
    myRepositoryManager = (GitTestRepositoryManager) myPlatformFacade.getRepositoryManager(myProject)
    myVcsHelper = (MockVcsHelper) myPlatformFacade.getVcsHelper(myProject)
  }

  void assertNotificationShown(Notification expected) {
    if (expected) {
      Notification actualNotification = (myPlatformFacade.getNotificator(myProject) as TestNotificator).lastNotification
      assertNotNull "No notification was shown", actualNotification
      assertEquals(stripLineBreaksAndHtml(expected.content), stripLineBreaksAndHtml(actualNotification.content))
    }
  }

  void assertNotificationShown(String title, String message, NotificationType type) {
    assertNotificationShown(new Notification(TEST_NOTIFICATION_GROUP, title, message, type))
  }

}
