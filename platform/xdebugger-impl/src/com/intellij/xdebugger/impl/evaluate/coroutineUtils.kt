// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus

/**
 * Please use this function only when really needed. Since returned [CoroutineScope] should be manually closed.
 * In 99.9% of cases CoroutineScope should be provided from top, instead of manual creation based on Editor, Project etc.
 */
@ApiStatus.Internal
@DelicateCoroutinesApi
fun Editor.childCoroutineScope(name: String): CoroutineScope {
  val coroutineScope = GlobalScope.childScope(name)
  val disposable = (this as? EditorImpl)?.disposable ?: project
  if (disposable != null) {
    Disposer.register(disposable) {
      coroutineScope.cancel()
    }
  }
  return coroutineScope
}