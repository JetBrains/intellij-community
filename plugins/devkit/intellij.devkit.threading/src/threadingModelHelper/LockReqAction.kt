// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper

import org.jetbrains.idea.devkit.threadingModelHelper.ConstraintType
import org.jetbrains.idea.devkit.threadingModelHelper.LOCK_REQUIREMENTS
import java.util.EnumSet


internal class LockReqAction : BaseReqSearchAction() {
  override val requirements: EnumSet<ConstraintType> = LOCK_REQUIREMENTS
}