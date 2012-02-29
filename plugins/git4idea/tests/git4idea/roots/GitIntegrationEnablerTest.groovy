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

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import git4idea.test.GitMockVirtualFile
import git4idea.test.GitTestPlatformFacade
import git4idea.test.MockGit
import git4idea.test.TestNotificator
import git4idea.tests.TestDialogHandler
import git4idea.tests.TestDialogManager
import org.junit.Before
import org.junit.Test

import java.lang.reflect.Field
import javax.swing.JRadioButton

import static git4idea.test.GitGTestUtil.stripLineBreaksAndHtml
import static git4idea.test.GitGTestUtil.toAbsolute
import static junit.framework.Assert.*

/**
 * 
 * @author Kirill Likhodedov
 */
class GitIntegrationEnablerTest {

  Project myProject
  GitTestPlatformFacade myPlatformFacade
  MockGit myGit
  String myProjectDir
  TestDialogManager myDialogManager

  @Before
  void setUp() {
    myProjectDir = FileUtil.createTempDirectory("git", null)

    myProject = [
            getBaseDir: { new GitMockVirtualFile(myProjectDir) }
    ] as Project

    myPlatformFacade = new GitTestPlatformFacade()
    myGit = new MockGit()
    myDialogManager = myPlatformFacade.getDialogManager()
  }

  @Test
  void "Simplest case - 1 root for the whole project => just add VCS root"() {
    doTest given(["."]),
           expect(git_init:  [])
  }

  @Test
  void "No Git roots => init & notify"() {
    doTest given( [], false ),
           expect( git_init: ["."],
                   vcs_roots: ["."],
                   notification("Created Git repository in $myProjectDir"))
  }

  @Test
  void "Below Git, no inside => notify"() {
    doTest given([".."], true, true),
           expect( git_init: [],
                   notification("Added Git root: ${path("..")}"))
  }

  @Test
  void "Git for project, some inside => notify"() {
    doTest given( [".", "community"]),
           expect(git_init: [],
                  notification("Added Git roots: ${path(".")}, ${path("community")}"))
  }

  @Test
  void "Below Git, some inside => notify"() {
    doTest given(["..", "community"], true, true),
           expect(git_init: [],
                  notification("Added Git roots: ${path("..")}, ${path("community")}"))
  }

  @Test
  void "Not under Git, some inside => show dialog"() {
    GitRootDetectInfo detectInfo = complexCaseDetectInfo()
    new GitIntegrationEnabler(myProject, myGit, myPlatformFacade).enable(detectInfo)
    assertDialogShown GitIntegrationEnableComplexCaseDialog
  }

  private GitRootDetectInfo complexCaseDetectInfo() {
    given(["community", "contrib"], false)
  }

  @Test
  public void "Select just add roots in the dialog"() {
    GitRootDetectInfo detectInfo = complexCaseDetectInfo()
    myDialogManager.registerDialogHandler(GitIntegrationEnableComplexCaseDialog,
                                          new TestDialogHandler<GitIntegrationEnableComplexCaseDialog>() {
                                            @Override
                                            int handleDialog(GitIntegrationEnableComplexCaseDialog dialog) {
                                              Field field = dialog.class.getDeclaredField("myJustAddRoots")
                                              field.setAccessible(true);
                                              ((JRadioButton)field.get(dialog)).setSelected(true);
                                              return DialogWrapper.OK_EXIT_CODE;
                                            }
                                          })
    new GitIntegrationEnabler(myProject, myGit, myPlatformFacade).enable(detectInfo)
    assertFalse ".git" in new File(myProjectDir).list()
    assertVcsRoots(toAbsolute(["community", "contrib"], myProject))
  }

  @Test
  public void "Select git init for the project in the dialog"() {
    GitRootDetectInfo detectInfo = complexCaseDetectInfo()
    myDialogManager.registerDialogHandler(GitIntegrationEnableComplexCaseDialog,
                                          new TestDialogHandler<GitIntegrationEnableComplexCaseDialog>() {
                                            @Override
                                            int handleDialog(GitIntegrationEnableComplexCaseDialog dialog) {
                                              return DialogWrapper.OK_EXIT_CODE;
                                            }
                                          })
    new GitIntegrationEnabler(myProject, myGit, myPlatformFacade).enable(detectInfo)
    assertTrue ".git" in new File(myProjectDir).list()
    assertVcsRoots(toAbsolute([".", "community", "contrib"], myProject))
  }

  void assertDialogShown(Class dialogClass) {
    assertNotNull "Dialog wasn't shown", myDialogManager.getLastShownDialog()
    assertTrue    "Incorrect dialog was shown", myDialogManager.getLastShownDialog().getClass().equals(dialogClass)
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
      assertTrue ".git" in new File(myProjectDir + "/" + it).list()
    }
  }

  void assertNotificationShown(Notification expected) {
    if (expected) {
      Notification actualNotification = (myPlatformFacade.getNotificator(myProject) as TestNotificator).lastNotification
      assertNotNull "No notification was shown", actualNotification
      assertEquals(stripLineBreaksAndHtml(expected.content), stripLineBreaksAndHtml(actualNotification.content))
    }
  }

  void assertVcsRoots(Collection<String> expectedVcsRoots) {
    VirtualFile[] actualRoots = myPlatformFacade.getVcsManager(myProject).getRootsUnderVcs(myPlatformFacade.getVcs(myProject))
    assertEquals expectedVcsRoots.toSet(), actualRoots.collect() { it.path }.toSet()
  }

  GitRootDetectInfo given(Collection<String> roots, boolean full = true, boolean below = false) {
    new GitRootDetectInfo(roots.collect { (VirtualFile)new GitMockVirtualFile(toAbsolute(it, myProject)) }, full, below)
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
    new File(myProjectDir + "/" + relativePath).canonicalPath
  }

}
