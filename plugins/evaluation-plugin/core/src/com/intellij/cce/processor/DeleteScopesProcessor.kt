// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.processor

import com.intellij.cce.actions.DeleteRange
import com.intellij.cce.core.CodeFragment

class DeleteScopesProcessor : GenerateActionsProcessor() {
  override fun process(code: CodeFragment) {
    addAction(DeleteRange(code.offset, code.offset + code.length))
  }
}