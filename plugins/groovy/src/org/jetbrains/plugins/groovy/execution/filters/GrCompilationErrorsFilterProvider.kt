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
package org.jetbrains.plugins.groovy.execution.filters

import com.intellij.execution.filters.*
import com.intellij.openapi.project.Project

class GrCompilationErrorsFilterProvider : ConsoleFilterProvider {

  override fun getDefaultFilters(project: Project): Array<Filter> = arrayOf(
    object : RegexpFilter(project, "(file:)?${FILE_PATH_MACROS}: ${LINE_MACROS}.*") {

      override fun createOpenFileHyperlink(fileName: String, line: Int, column: Int): HyperlinkInfo {
        return super.createOpenFileHyperlink(fileName, line, column)
               ?: LazyFileHyperlinkInfo(project, fileName, line, column)
      }
    }
  )
}
