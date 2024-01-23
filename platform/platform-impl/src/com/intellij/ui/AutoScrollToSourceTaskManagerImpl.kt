// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.Utils.createAsyncDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.util.await
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.util.OpenSourceUtil
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
internal class AutoScrollToSourceTaskManagerImpl : AutoScrollToSourceTaskManager {
  @RequiresEdt
  override fun scheduleScrollToSource(handler: AutoScrollToSourceHandler, dataContext: DataContext) {
    val asyncDataContext = createAsyncDataContext(dataContext)
    val project = dataContext.getData(PlatformDataKeys.PROJECT)

    // the task must be canceled if the project is closed
    ((project as? ComponentManagerEx)?.getCoroutineScope() ?: service<CoreUiCoroutineScopeHolder>().coroutineScope)
      .launch(Dispatchers.EDT + ClientId.current.asContextElement()) {
      PlatformDataKeys.TOOL_WINDOW.getData(asyncDataContext)
        ?.getReady(handler)
        ?.await()

      val navigatable = readAction {
        if (handler.canAutoScrollTo(CommonDataKeys.VIRTUAL_FILE.getData(asyncDataContext))) {
          CommonDataKeys.NAVIGATABLE_ARRAY.getData(asyncDataContext)?.singleOrNull()
        }
        else {
          null
        }
      }

      if (navigatable != null) {
        SlowOperations.knownIssue("IDEA-304701, EA-677533").use {
          OpenSourceUtil.navigateToSource(false, true, navigatable)
        }
      }
    }
  }
}

private fun AutoScrollToSourceHandler.canAutoScrollTo(file: VirtualFile?) = file == null || isAutoScrollEnabledFor(file)
