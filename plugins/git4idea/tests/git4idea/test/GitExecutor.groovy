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

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ArrayUtil

/**
 *
 * @author Kirill Likhodedov
 */
class GitExecutor {

  private String myCurrentDir

  void cd(String path) {
    myCurrentDir = path
  }

  String git(String command) {
    List<String> split = StringUtil.split(command, " ")
    String[] params = split.size() > 1 ? ArrayUtil.toObjectArray(split.subList(1, split.size()), String) : ArrayUtil.EMPTY_STRING_ARRAY
    return new GitTestRunEnv(new File(myCurrentDir)).run(split.get(0), params);
  }

}
