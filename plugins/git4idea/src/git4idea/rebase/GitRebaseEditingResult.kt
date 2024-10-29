// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import git4idea.rebase.GitRebaseEditorHandler.ERROR_EXIT_CODE

sealed class GitRebaseEditingResult(val exitCode: Int) {
  data object CommitListEditorCancelled : GitRebaseEditingResult(ERROR_EXIT_CODE)
  data object UnstructuredEditorCancelled : GitRebaseEditingResult(ERROR_EXIT_CODE)
  data object WasEdited : GitRebaseEditingResult(0)

  class Failed(val cause: Exception) : GitRebaseEditingResult(ERROR_EXIT_CODE)
}