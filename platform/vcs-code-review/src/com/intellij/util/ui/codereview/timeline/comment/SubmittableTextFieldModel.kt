// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview.timeline.comment

import com.intellij.openapi.editor.Document
import com.intellij.util.ui.codereview.SimpleEventListener

interface SubmittableTextFieldModel {
  val document: Document

  val isBusy: Boolean

  val error: Throwable?

  fun submit()

  fun addStateListener(listener: SimpleEventListener)
}