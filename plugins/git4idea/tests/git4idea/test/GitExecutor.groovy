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

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ArrayUtil
import git4idea.repo.GitRepository

/**
 *
 * @author Kirill Likhodedov
 */
class GitExecutor {

  private String myCurrentDir

  def shortenPath(String path) {
    def split = path.split("/")
    if (split.size() > 3) {
      // split[0] is empty, because the path starts from /
      return "/${split[1]}/.../${split[-2]}/${split[-1]}"
    }
    return path
  }

  def cd(String path) {
    myCurrentDir = path
    println "cd ${shortenPath(path)}"
  }

  def cd(GitRepository repository) {
    cd repository.root.path
  }

  String git(String command) {
    List<String> split = StringUtil.split(command, " ")
    String[] params = split.size() > 1 ? ArrayUtil.toObjectArray(split.subList(1, split.size()), String) : ArrayUtil.EMPTY_STRING_ARRAY
    return new GitTestRunEnv(new File(myCurrentDir)).run(split.get(0), params);
  }

  String git(GitRepository repository, String command) {
    cd repository.root.path
    git command
  }

  def touch(String fileName) {
    File file = new File(myCurrentDir, fileName)
    assert !file.exists()
    file.createNewFile()
    println("touch $fileName")
    file.path
  }

  def touch(String fileName, String content) {
    touch(fileName)
    echo(fileName, content)
  }

  def echo(String fileName, String content) {
    new File(myCurrentDir, fileName).withWriterAppend("UTF-8") { it.write(content) }
  }

  def mkdir(String dirName) {
    File file = new File(myCurrentDir, dirName)
    file.mkdir()
    println("mkdir $dirName")
    file.path
  }

  def cat(String fileName) {
    def content = FileUtil.loadFile(new File(myCurrentDir, fileName))
    println("cat fileName")
    content
  }

}
