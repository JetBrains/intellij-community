// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper

import org.jetbrains.idea.devkit.threadingModelHelper.ConstraintType
import org.jetbrains.idea.devkit.threadingModelHelper.THREAD_REQUIREMENTS
import java.util.*

internal class ThreadingReqAction: BaseReqSearchAction() {
  override val requirements: EnumSet<ConstraintType> = THREAD_REQUIREMENTS
}
