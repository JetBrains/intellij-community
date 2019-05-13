// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project

class GroovyConsoleView(project: Project) : ConsoleViewImpl(project, true) {

  companion object {
    private const val RESULT_MARKER = "ee2d5778-e2f4-4705-84ef-0847535c32f4"
  }

  override fun print(text: String, contentType: ConsoleViewContentType) {
    if (text.startsWith(RESULT_MARKER)) {
      super.print("Result: ", ConsoleViewContentType.SYSTEM_OUTPUT)
      super.print(text.substring(RESULT_MARKER.length), contentType)
    }
    else {
      super.print(text, contentType)
    }
  }
}
