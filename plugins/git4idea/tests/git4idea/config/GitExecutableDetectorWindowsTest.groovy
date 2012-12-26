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
package git4idea.config
import com.intellij.openapi.util.io.FileUtil
import git4idea.test.GitTestUtil
import org.jetbrains.annotations.NotNull
import org.junit.Before
import org.junit.Test

import java.lang.reflect.Field
import java.lang.reflect.Modifier

import static junit.framework.Assert.assertEquals

/**
 *
 * @author Kirill Likhodedov
 */
class GitExecutableDetectorWindowsTest {

  private File testRoot
  private def canRun = []

  @Before
  void setUp() {
    GitTestUtil.setWindows(true);
    testRoot = FileUtil.createTempDirectory("", "")
    setWindowsRoot(testRoot);
  }

  @Test
  void "Simple case"() {
    fs "/Program Files/Git/bin/git.exe"
    assertExecutable "C:/Program Files/Git/bin/git.exe"
  }

  @Test
  void "Prefer default Git without version to versioned ones"() {
    fs "/Program Files/Git/bin/git.exe", "/Program Files/Git 1.8/bin/git.exe", "/Program Files/Git_1.7.1/bin/git.exe"
    assertExecutable "C:/Program Files/Git/bin/git.exe"
  }

  @Test
  void "Prefer the latest version"() {
    fs "/Program Files (x86)/Git 1.8/cmd/git.exe",
       "/Program Files/Git_1.7.1/bin/git.exe",
       "/Program Files/Git_1.7.5/cmd/git.cmd",
       "/Program Files (x86)/Git_1.7.0.2/bin/git.exe"
    assertExecutable "C:/Program Files (x86)/Git 1.8/cmd/git.exe"
  }

  @Test
  void "Prefer Program Files over x86"() {
    fs "/Program Files (x86)/Git 1.8/bin/git.exe", "/Program Files/Git 1.8/bin/git.exe"
    assertExecutable "C:/Program Files/Git 1.8/bin/git.exe"
  }

  @Test
  void "Prefer git.cmd over git.exe"() {
    fs "/Program Files (x86)/Git 1.7.4/bin/git.exe", "/Program Files/Git 1.7.4/cmd/git.cmd"
    assertExecutable "C:/Program Files/Git 1.7.4/cmd/git.cmd"
  }

  @Test
  void "Prefer cmd over bin in newer versions of Git"() {
    fs "/Program Files (x86)/Git 1.8/bin/git.exe", "/Program Files/Git 1.8/cmd/git.exe"
    assertExecutable "C:/Program Files/Git 1.8/cmd/git.exe"
  }

  @Test
  void "1.8.0 Prefer cmd/git.cmd over cmd/git.exe and bin/git.exe"() {
    fs "/Program Files (x86)/Git_1.8/bin/git.exe",
       "/Program Files (x86)/Git_1.8/cmd/git.cmd",
       "/Program Files (x86)/Git_1.8/cmd/git.exe"
    assertExecutable "C:/Program Files (x86)/Git_1.8/cmd/git.cmd"
  }

  @Test
  void "Prefer msys over cygwin"() {
    fs "/Program Files (x86)/Git_1.8.0.2/cmd/git.exe",
       "/Program Files (x86)/Git_1.8.0.2/bin/git.exe",
       "/cygwin/bin/git.exe"
    assertExecutable "C:/Program Files (x86)/Git_1.8.0.2/cmd/git.exe"
  }

  @Test
  void "Only cygwin"() {
    fs "/cygwin/bin/git.exe"
    assertExecutable "C:/cygwin/bin/git.exe"
  }

  @Test
  void "Many different versions, real case"() {
    fs "/Program Files (x86)/Git_1.7.0.2/bin/git.exe",
       "/Program Files (x86)/Git_1.7.0.2/cmd/git.cmd",
       "/Program Files (x86)/Git_1.7.8/bin/git.exe",
       "/Program Files (x86)/Git_1.7.8/cmd/git.cmd",
       "/Program Files (x86)/Git_1.8/bin/git.exe",
       "/Program Files (x86)/Git_1.8/cmd/git.cmd",
       "/Program Files (x86)/Git_1.8/cmd/git.exe",
       "/Program Files (x86)/Git_1.8.0.2/cmd/git.exe",
       "/Program Files (x86)/Git_1.8.0.2/bin/git.exe",
       "/cygwin/bin/git.exe"
    assertExecutable "C:/Program Files (x86)/Git_1.8.0.2/cmd/git.exe"
  }

  @Test
  void "Program not found, try git.exe"() {
    canRun = [ "git.exe" ]
    assertExecutable "git.exe"
  }

  @Test
  void "For both git.exe and git.cmd prefer git.cmd"() {
    canRun = [ "git.exe", "git.cmd" ]
    assertExecutable "git.cmd"
  }

  def assertExecutable(String expected) {
    expected = FileUtil.toSystemDependentName(expected).replace("C:", testRoot.path)
    assertEquals "Incorrect executable detected", expected, detect()
  }

  def fs(String... paths) {
    paths.each {
      mkPath(it)
    }
  }

  def mkPath(String path) {
    def file = new File(testRoot.getPath() + FileUtil.toSystemDependentName(path))
    file.getParentFile().mkdirs()
    file.createNewFile()
  }

  private String detect() {
    new GitExecutableDetector() {
      @Override
      protected boolean runs(@NotNull String exec) {
        return canRun.contains(exec);
      }
    }.detect();
  }

  static def setWindowsRoot(File file) {
    Field field = GitExecutableDetector.class.getDeclaredField("WIN_ROOT");
    field.setAccessible(true);

    Field modifiersField = Field.class.getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

    field.set(null, file);
  }

}
