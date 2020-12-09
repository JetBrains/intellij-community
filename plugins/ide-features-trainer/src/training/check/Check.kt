// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.check

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

interface Check {
  fun set(project: Project, editor: Editor)
  fun before()
  fun check(): Boolean
}