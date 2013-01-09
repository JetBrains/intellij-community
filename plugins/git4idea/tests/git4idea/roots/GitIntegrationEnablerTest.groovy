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
package git4idea.roots

import com.intellij.dvcs.test.MockVirtualFile
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.vfs.VirtualFile
import git4idea.test.GitLightTest
import git4idea.test.TestNotificator
import org.junit.After
import org.junit.Before
import org.junit.Test

import static git4idea.test.GitGTestUtil.toAbsolute
import static junit.framework.Assert.*

/**
 * 
 * @author Kirill Likhodedov
 */
class GitIntegrationEnablerTest extends GitLightTest {

  public static final String TEST_NOTIFICATION_GROUP = "Test"

  @Override
  @Before
  public void setUp() {
    super.setUp();
  }

  @After
  public void tearDown() {
    super.tearDown();
  }

  @Test
  void "1 root for the whole project, then just add VCS root"() {
    doTest given(["."]),
           expect(git_init:  [])
  }

  @Test
  void "No Git roots, then init & notify"() {
    doTest given( [], false ),
           expect( git_init: ["."],
                   vcs_roots: ["."],
                   notification("Created Git repository in $myProjectRoot"))
  }

  @Test
  void "Below Git, no inside, then notify"() {
    doTest given([".."], true, true),
           expect( git_init: [],
                   notification("Added Git root: ${path("..")}"))
  }

  @Test
  void "Git for project, some inside, then notify"() {
    doTest given( [".", "community"]),
           expect(git_init: [],
                  notification("Added Git roots: ${path(".")}, ${path("community")}"))
  }

  @Test
  void "Below Git, some inside, then notify"() {
    doTest given(["..", "community"], true, true),
           expect(git_init: [],
                  notification("Added Git roots: ${path("..")}, ${path("community")}"))
  }

  @Test
  void "Not under Git, some inside, then notify"() {
    doTest given( ["community", "contrib"], false, false),
           expect(git_init: [],
                  notification("Added Git roots: ${path("community")}, ${path("contrib")}"))
  }

  private void doTest(GitRootDetectInfo detectInfo, Map map) {

    // defaults
    if (!map.vcs_roots) {
      map.vcs_roots = detectInfo.roots.collect { it.path }
    }
    else {
      map.vcs_roots = toAbsolute(map.vcs_roots.toList(), myProject)
    }

    new GitIntegrationEnabler(myProject, myGit, myPlatformFacade).enable(detectInfo)

    assertVcsRoots map.vcs_roots
    assertGitInit map.git_init
    assertNotificationShown map.notification
  }

  void assertGitInit(Collection<String> roots) {
    roots.each {
      assertTrue ".git" in new File(myProjectRoot + "/" + it).list()
    }
  }

  void assertVcsRoots(Collection<String> expectedVcsRoots) {
    VirtualFile[] actualRoots = myPlatformFacade.getVcsManager(myProject).getRootsUnderVcs(myPlatformFacade.getVcs(myProject))
    assertEquals expectedVcsRoots.toSet(), actualRoots.collect() { it.path }.toSet()
  }

  GitRootDetectInfo given(Collection<String> roots, boolean full = true, boolean below = false) {
    new GitRootDetectInfo(roots.collect { (VirtualFile)new MockVirtualFile(toAbsolute(it, myProject)) }, full, below)
  }

  Map expect(Map map, Notification notification = null, Class dialogClass = null) {
    map.notification = notification
    map.dialogClass = dialogClass
    map
  }

  Notification notification(String content) {
    new Notification("Test", "", content, NotificationType.INFORMATION)
  }

  String path(String relativePath) {
    new File(myProjectRoot + "/" + relativePath).canonicalPath
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
