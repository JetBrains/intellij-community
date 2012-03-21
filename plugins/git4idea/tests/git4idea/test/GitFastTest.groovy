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

import static git4idea.test.GitGTestUtil.stripLineBreaksAndHtml
import static junit.framework.Assert.assertEquals
import static junit.framework.Assert.assertNotNull
import com.intellij.openapi.project.Project
import git4idea.tests.TestDialogManager

/**
 * 
 * @author Kirill Likhodedov
 */
class GitFastTest {

  Project myProject
  GitTestPlatformFacade myPlatformFacade
  MockGit myGit
  String myProjectDir
  TestDialogManager myDialogManager

  void assertNotificationShown(Notification expected) {
    if (expected) {
      Notification actualNotification = (myPlatformFacade.getNotificator(myProject) as TestNotificator).lastNotification
      assertNotNull "No notification was shown", actualNotification
      assertEquals(stripLineBreaksAndHtml(expected.content), stripLineBreaksAndHtml(actualNotification.content))
    }
  }

}
