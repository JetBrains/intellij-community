package git4idea
import com.intellij.dvcs.test.MockProject
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import git4idea.test.GitTestImpl
import git4idea.test.GitTestPlatformFacade

import static com.intellij.dvcs.test.Executor.cd
import static com.intellij.dvcs.test.Executor.mkdir
import static com.intellij.dvcs.test.Executor.touch
import static cucumber.runtime.groovy.EN.Given
import static cucumber.runtime.groovy.Hooks.After
import static cucumber.runtime.groovy.Hooks.Before
import static git4idea.GitCucumberWorld.*
import static git4idea.test.GitExecutor.git
import static git4idea.test.GitScenarios.checkout
import static git4idea.test.GitTestInitUtil.createRepository

/**
 * General step definitions and hooks used by all cucumber tests.
 *
 * @author Kirill Likhodedov
 */

Before() {
  myTestRoot = FileUtil.createTempDirectory("", "").getPath()
  cd myTestRoot
  myProjectRoot = mkdir ("project")
  myProject = new MockProject(myProjectRoot)
  myPlatformFacade = new GitTestPlatformFacade()
  myGit = new GitTestImpl()

  cd(myProjectRoot)
  myRepository = createRepository(myProjectRoot, myPlatformFacade, myProject)
}

After() {
  FileUtil.delete(new File(myTestRoot))
  Disposer.dispose(myProject)
}

Given(~'^file (.*) on master:$') { String filename, String content ->
  checkout(myRepository, "master")
  touch(filename, content)
  git("add $filename")
  git("commit -m 'adding $filename'")
}