// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.AnActionEvent

interface DiffPreview {
  fun updateAvailability(event: AnActionEvent) = Unit
  fun updatePreview(fromModelRefresh: Boolean) = Unit
  fun setPreviewVisible(isPreviewVisible: Boolean, focus: Boolean = false) = Unit
}
