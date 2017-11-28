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
package org.jetbrains.plugins.groovy.console

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project

class GroovyConsoleView(project: Project) : ConsoleViewImpl(project, true) {

  companion object {
    private val RESULT_MARKER = "ee2d5778-e2f4-4705-84ef-0847535c32f4"
    private val COMMAND_END_MARKER = "01bd8da7-84b0-4f52-9f98-fd15f4dbc1a7"
  }

  override fun print(text: String, contentType: ConsoleViewContentType) {
    if (text.startsWith(RESULT_MARKER)) {
      super.print("Result: ", ConsoleViewContentType.SYSTEM_OUTPUT)
      super.print(text.substring(RESULT_MARKER.length), contentType)
    }
    else if (text.startsWith(COMMAND_END_MARKER)) {
      super.print("\n", contentType)
    }
    else {
      super.print(text, contentType)
    }
  }
}
