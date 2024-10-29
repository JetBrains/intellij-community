// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.util.FileTextUtil.computeChecksum

class ActionsGenerator(private val processor: GenerateActionsProcessor) {
  fun generate(code: CodeFragment): FileActions {
    val actions = processor.buildActions(code)
    return FileActions(code.path, computeChecksum(code.text), actions.count { it is CallFeature }, actions)
  }
}
