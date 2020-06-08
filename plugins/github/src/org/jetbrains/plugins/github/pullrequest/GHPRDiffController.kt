// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.Disposable
import com.intellij.openapi.ListSelection
import com.intellij.openapi.vcs.changes.Change

interface GHPRDiffController {

  var selection: ListSelection<Change>?

  fun addAndInvokeSelectionListener(disposable: Disposable, listener: () -> Unit)
}
