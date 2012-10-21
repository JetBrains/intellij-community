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

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.io.FileUtil

/**
 * 
 * @author Kirill Likhodedov
 */
class GitGTestUtil {

  static String toAbsolute(String relPath, Project project) {
    new File(toAbsolute(Collections.singletonList(relPath), project)[0]).mkdir()
    toAbsolute(Collections.singletonList(relPath), project)[0]
  }

  static Collection<String> toAbsolute(Collection<String> relPaths, Project project) {
    relPaths.collect { FileUtil.toSystemIndependentName new File(project.baseDir.path + "/" + it).getCanonicalPath() }
  }

  static String stripLineBreaksAndHtml(String s) {
    StringUtil.stripHtml(s, true).replace('\n', '');
  }

  static String stripLineBreaksAndMultiSpaces(String s) {
    s.replace('\n', '').replaceAll(" {3,}", " ")
  }

}
