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

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.ini4j.Ini
import java.io.File
import java.io.IOException

private val LOG: Logger get() = logger(::LOG)

@Throws(IOException::class)
internal fun loadIniFile(file: File): Ini {
  val ini = Ini()
  ini.config.isMultiOption = true  // duplicate keys (e.g. url in [remote])
  ini.config.isTree = false        // don't need tree structure: it corrupts url in section name (e.g. [url "http://github.com/"]
  ini.config.isLowerCaseOption = true
  try {
    ini.load(file)
    return ini
  }
  catch (e: IOException) {
    LOG.warn("Couldn't load config file at ${file.path}", e)
    throw e
  }
}

internal fun findClassLoader(): ClassLoader? {
  val javaClass = ::findClassLoader.javaClass
  val plugin = PluginManager.getPlugin(PluginManagerCore.getPluginByClassName(javaClass.name))
  return plugin?.pluginClassLoader ?: javaClass.classLoader  // null e.g. if IDEA is started from IDEA
}
