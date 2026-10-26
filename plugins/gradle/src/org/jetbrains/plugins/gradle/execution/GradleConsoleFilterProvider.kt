/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.execution

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.RegexpFilter
import com.intellij.execution.filters.RegexpFilter.COLUMN_MACROS
import com.intellij.execution.filters.RegexpFilter.FILE_PATH_MACROS
import com.intellij.execution.filters.RegexpFilter.LINE_MACROS
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleSettings

private val SCRIPT_LOCATION_EXPRESSION = "(?:[Bb]uild file|[Ss]ettings file|Initialization script|Script) %s'(?: line)?: %s"
  .format(FILE_PATH_MACROS, LINE_MACROS)
private val FILE_LOCATION_EXPRESSION = "%s:%s(?::%s)?".format(FILE_PATH_MACROS, LINE_MACROS, COLUMN_MACROS)

@Suppress("SplitModeApiUsage")
class GradleConsoleFilterProvider : ConsoleFilterProvider {

  class GradleFileLocationRegexpFilter(project: Project) : RegexpFilter(project, FILE_LOCATION_EXPRESSION)

  class GradleConsoleFilter(project: Project?) : RegexpFilter(project, SCRIPT_LOCATION_EXPRESSION)

  override fun getDefaultFilters(project: Project): Array<Filter> {
    if (GradleSettings.getInstance(project).linkedProjectsSettings.isEmpty()) {
      return emptyArray()
    }
    return arrayOf(
      GradleConsoleFilter(project),
      GradleFileLocationRegexpFilter(project),
    )
  }
}
