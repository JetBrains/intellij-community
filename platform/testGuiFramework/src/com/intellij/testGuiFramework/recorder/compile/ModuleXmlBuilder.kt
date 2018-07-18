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
package com.intellij.testGuiFramework.recorder.compile

/**
 * @author Sergey Karashevich
 */
object ModuleXmlBuilder {

  private fun module(function: () -> String): String = """<modules>
${function.invoke()}
</modules>"""

  private fun modules(outputDir: String, function: () -> String): String =
    """<module name="main" outputDir="$outputDir" type="java-production">
${function.invoke()}
</module>"""

  private fun addSource(path: String) = """<sources path="$path"/>"""

  private fun addClasspath(path: String) = """<classpath path="$path"/>"""


  fun build(outputDir: String, classPath: List<String>, sourcePath: String): String =
    module {
      modules(outputDir = outputDir) {
        classPath
          .map { path -> addClasspath(path) }
          .plus(addSource(sourcePath))
          .joinToString("\n")
      }
    }
}