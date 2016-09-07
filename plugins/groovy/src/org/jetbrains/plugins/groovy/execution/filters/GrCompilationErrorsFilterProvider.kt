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
package org.jetbrains.plugins.groovy.execution.filters

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.RegexpFilter
import com.intellij.execution.filters.RegexpFilter.FILE_PATH_MACROS
import com.intellij.execution.filters.RegexpFilter.LINE_MACROS
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_OBJECT

class GrCompilationErrorsFilterProvider : ConsoleFilterProvider {

  override fun getDefaultFilters(project: Project): Array<Filter> {
    JavaPsiFacade.getInstance(project).findClass(GROOVY_OBJECT, GlobalSearchScope.allScope(project)) ?: return Filter.EMPTY_ARRAY
    return arrayOf(RegexpFilter(project, "${FILE_PATH_MACROS}: ${LINE_MACROS}.*"))
  }
}
