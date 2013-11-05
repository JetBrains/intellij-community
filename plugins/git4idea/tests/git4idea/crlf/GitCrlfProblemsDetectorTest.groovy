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
package git4idea.crlf
import com.intellij.dvcs.test.MockVirtualFile
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import git4idea.test.GitLightTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import static com.intellij.openapi.vcs.Executor.cd
import static git4idea.test.GitExecutor.git
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue
/**
 *
 * @author Kirill Likhodedov
 */
@Ignore
class GitCrlfProblemsDetectorTest extends GitLightTest {

  private String myOldGlobalAutoCrlfValue
  private String myOldSystemAutoCrlfValue

  @Before
  public void setUp() {
    super.setUp();
    cd myProjectRoot
    try {
      myOldGlobalAutoCrlfValue = git ("config --global core.autocrlf")
      git ("config --global --unset core.autocrlf")
    } catch (Exception e) {
      System.out.println("Couldn't operate with global config: " + e.getMessage())
    }
    myOldSystemAutoCrlfValue = git ("config --system core.autocrlf")
    git ("config --system --unset core.autocrlf")

    createRepository(myProjectRoot)
  }

  @After
  public void tearDown() {
    if (!StringUtil.isEmptyOrSpaces(myOldGlobalAutoCrlfValue)) {
      git ("config --global core.autocrlf " + myOldGlobalAutoCrlfValue);
    }
    if (!StringUtil.isEmptyOrSpaces(myOldSystemAutoCrlfValue)) {
      git ("config --global core.autocrlf " + myOldSystemAutoCrlfValue);
    }
    super.tearDown();
  }

  @Test
  void "autocrlf is true = warning"() {
    git ("config core.autocrlf true")
    assertFalse "No warning should be done if core.autocrlf is true", detect("temp").shouldWarn()
  }

  @Test
  void "autocrlf is input = no warning"() {
    git ("config core.autocrlf input")
    assertFalse "No warning should be done if core.autocrlf is input", detect("temp").shouldWarn()
  }

  @Test
  void "no files with CRLF = no warning"() {
    createFile("temp", "Unix file\nNice separators\nOnly LF\n")
    assertFalse "No warning should be done if all files are LFs", detect("temp").shouldWarn()
  }

  @Test
  void "file with CRLF, no attrs, autocrlf is false = warning"() {
    createCrlfFile("win")
    assertFalse "Warning should be done for a CRLF file with core.autocrlf = false", detect("temp").shouldWarn()
  }

  @Test
  void "file with CRLF, but text is set = no warning"() {
    gitattributes("*       text=auto")
    createCrlfFile("win")
    assertFalse "No warning should be done if the file has a text attribute", detect("win").shouldWarn()
  }

  @Test
  void "file with CRLF, but crlf is set = no warning"() {
    gitattributes("win       crlf")
    createCrlfFile("win")
    assertFalse "No warning should be done if the file has a crlf attribute", detect("win").shouldWarn()
  }

  @Test
  void "file with CRLF, but crlf is explicitly unset = no warning"() {
    gitattributes("win       -crlf")
    createCrlfFile("win")
    assertFalse "No warning should be done if the file has an explicitly unset crlf attribute", detect("win").shouldWarn()
  }

  @Test
  void "file with CRLF, but crlf is set to input = no warning"() {
    gitattributes("wi*       crlf=input")
    createCrlfFile("win")
    assertFalse "No warning should be done if the file has a crlf attribute", detect("win").shouldWarn()
  }

  @Test
  void "file with CRLF, nothing is set = warning"() {
    createCrlfFile("win")
    assertTrue "Warning should be done if the file has CRLFs inside, and no explicit attributes", detect("win").shouldWarn()
  }

  @Test
  void "various files with various attributes, one doesn't match = warning"() {
    gitattributes """
win1 text crlf diff
win2 -text
win3 text=auto
win4 crlf
win5 -crlf
win6 crlf=input
"""

    createFile("unix", "Unix file\nNice separators\nOnly LF\n")
    createCrlfFile("win1")
    createCrlfFile("win2")
    createCrlfFile("win3")
    createCrlfFile("src/win4")
    createCrlfFile("src/win5")
    createCrlfFile("src/win6")
    createCrlfFile("src/win7")

    assertTrue "Warning should be done, since one of the files has CRLFs and no related attributes",
           GitCrlfProblemsDetector.detect(myProject, myPlatformFacade, myGit,
                                          ["unix", "win1", "win2", "win3", "src/win4", "src/win5", "src/win6", "src/win7"]
                                                  .collect { it -> MockVirtualFile.fromPath(it, myProjectRoot) as VirtualFile})
                   .shouldWarn()
  }

  private void gitattributes(String content) {
    createFile(".gitattributes", content)
  }

  private GitCrlfProblemsDetector detect(String relPath) {
    return detect(createVirtualFile(relPath))
  }

  private GitCrlfProblemsDetector detect(VirtualFile file) {
    GitCrlfProblemsDetector.detect(myProject, myPlatformFacade, myGit, Collections.singleton(file))
  }

  private void createCrlfFile(String relPath) {
    createFile(relPath, "Windows file\r\nBad separators\r\nCRLF in action\r\n")
  }

  private void createFile(String relPath, String content) {
    String path = createFile(relPath)
    File file = new File(path)
    FileUtil.appendToFile(file, content)
  }

  private VirtualFile createVirtualFile(String relPath) {
    return new MockVirtualFile(createFile(relPath))
  }

  private String createFile(String relPath) {
    List<String> split = StringUtil.split(relPath, "/")
    File parent = new File(myProjectRoot)
    for (Iterator<String> it = split.iterator(); it.hasNext(); ) {
      String item = it.next()
      File file = new File(parent, item)
      if (it.hasNext()) {
        parent = file
        parent.mkdir()
      }
      else {
        file.createNewFile()
        return file.getPath()
      }
    }
    return parent;
  }

}
