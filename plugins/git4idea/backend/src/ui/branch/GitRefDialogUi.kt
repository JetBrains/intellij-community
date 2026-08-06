// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.textCompletion.TextCompletionProvider
import com.intellij.util.textCompletion.TextFieldWithCompletion

internal class GitRefDialogUi(
  project: Project,
  completionProvider: TextCompletionProvider,
  message: @NlsContexts.Label String,
) {

  @JvmField
  val textField: TextFieldWithCompletion =
    TextFieldWithCompletion(project, completionProvider, "", true, true, false)

  @JvmField
  val panel = panel {
    row {
      cell(textField)
        .label(message, position = LabelPosition.TOP)
        .align(AlignX.FILL)
    }
  }
}
