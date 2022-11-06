// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.Utils.wrapToAsyncDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.await
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.OpenSourceUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
private class AutoScrollToSourceTaskManagerImpl : AutoScrollToSourceTaskManager,
                                                  Disposable {

  private val scope = CoroutineScope(SupervisorJob())

  init {
    Disposer.register(ApplicationManager.getApplication(), this)
  }

  override fun dispose() {
    scope.cancel()
  }

  @RequiresEdt
  override fun scheduleScrollToSource(
    handler: AutoScrollToSourceHandler,
    dataContext: DataContext,
  ) {
    val asyncDataContext = wrapToAsyncDataContext(dataContext)

    scope.launch(Dispatchers.EDT) {
      PlatformDataKeys.TOOL_WINDOW.getData(asyncDataContext)
        ?.getReady(handler)
        ?.await()

      val navigatable = readAction {
        if (handler.canAutoScrollTo(CommonDataKeys.VIRTUAL_FILE.getData(asyncDataContext)))
          CommonDataKeys.NAVIGATABLE_ARRAY.getData(asyncDataContext)?.singleOrNull()
        else
          null
      }

      if (navigatable != null) {
        OpenSourceUtil.navigateToSource(false, true, navigatable)
      }
    }
  }
}

private fun AutoScrollToSourceHandler.canAutoScrollTo(file: VirtualFile?) =
  file == null || isAutoScrollEnabledFor(file)
