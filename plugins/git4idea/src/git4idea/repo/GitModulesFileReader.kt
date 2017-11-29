/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea.repo

import com.intellij.openapi.diagnostic.logger
import org.ini4j.Ini
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

class GitModulesFileReader {
  private val LOG = logger<GitModulesFileReader>()
  private val MODULE_SECTION = Pattern.compile("submodule \"(.*)\"", Pattern.CASE_INSENSITIVE)

  fun read(file: File): Collection<GitSubmoduleInfo> {
    if (!file.exists()) return listOf()

    val ini: Ini
    try {
      ini = loadIniFile(file)
    }
    catch (e: IOException) {
      return listOf()
    }
    val classLoader = findClassLoader()

    val modules = mutableSetOf<GitSubmoduleInfo>()
    for ((sectionName, section) in ini) {
      val matcher = MODULE_SECTION.matcher(sectionName)
      if (matcher.matches() && matcher.groupCount() == 1) {
        val bean = section.`as`(ModuleBean::class.java, classLoader)
        val path = bean.getPath()
        val url = bean.getUrl()
        if (path == null || url == null) {
          LOG.warn("Partially defined submodule: " + section.toString())
        }
        else {
          val module = GitSubmoduleInfo(path, url)
          LOG.debug("Found submodule " + module)
          modules.add(module)
        }
      }
    }
    return modules
  }

  private interface ModuleBean {
    fun getPath(): String?
    fun getUrl(): String?
  }
}