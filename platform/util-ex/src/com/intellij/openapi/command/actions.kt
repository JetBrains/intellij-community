// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command

import com.intellij.openapi.project.Project

inline fun executeCommand(project: Project? = null,
                          name: String? = null,
                          groupId: String? = null,
                          crossinline command: () -> Unit) {
  CommandProcessor.getInstance().executeCommand(project, Runnable { command() }, name, groupId)
}
