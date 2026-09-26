// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog.diagnostics

import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.attach.XAttachHost
import javax.swing.Icon

class ProcessesFetchingProblemException(val icon: Icon,
                                        @NlsContexts.ListItem
                                        val descriptionDisplayText: String,
                                        val action: ProcessesFetchingProblemAction?): ExecutionException(descriptionDisplayText)

class ProcessesFetchingProblemAction(
  val actionId: String,
  @NlsContexts.ListItem
  val actionName: String,
  val action: suspend (Project, XAttachHost, ProgressIndicator) -> Unit
)