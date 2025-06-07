// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.annotate.AnnotationWarning

internal object AnnotateDataKeys {
  @JvmField
  val WARNING_DATA: Key<AnnotationWarningUserData> = Key.create<AnnotationWarningUserData>("git.annotate.editor.warning")
}

internal class AnnotationWarningUserData(val warning: AnnotationWarning, val forceAnnotate: Runnable)