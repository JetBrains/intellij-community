// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

@Deprecated("Causes memory leaks due to incorrect Disposable usage",
            replaceWith = ReplaceWith("GitHistoryTraverser.create(project, parentDisposable)", "git4idea.history.GitHistoryTraverser"),
            level = DeprecationLevel.ERROR)
fun getTraverser(project: Project): GitHistoryTraverser? {
  return GitHistoryTraverser.create(project, Disposer.newDisposable("Git History Traverser"))
}