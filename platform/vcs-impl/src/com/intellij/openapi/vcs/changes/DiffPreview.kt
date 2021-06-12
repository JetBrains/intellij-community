// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

interface DiffPreview {
  fun updatePreview(fromModelRefresh: Boolean) = Unit
  fun setPreviewVisible(isPreviewVisible: Boolean, focus: Boolean = false) = Unit
}
