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
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.util.await
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.util.OpenSourceUtil
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class AutoScrollToSourceTaskManagerImpl : AutoScrollToSourceTaskManager {
  @RequiresEdt
  override fun scheduleScrollToSource(handler: AutoScrollToSourceHandler, dataContext: DataContext) {
    val asyncDataContext = createAsyncDataContext(dataContext)
    val project = dataContext.getData(PlatformDataKeys.PROJECT)

    // the task must be canceled if the project is closed
    (project?.service<CoreUiCoroutineScopeHolder>() ?: service<CoreUiCoroutineScopeHolder>()).coroutineScope
      .launch(ClientId.current.asContextElement()) {
        PlatformDataKeys.TOOL_WINDOW.getData(asyncDataContext)?.getReady(handler)?.await()

        val navigatable = readAction {
          val file = CommonDataKeys.VIRTUAL_FILE.getData(asyncDataContext)
          if (file == null || handler.isAutoScrollEnabledFor(file)) {
            CommonDataKeys.NAVIGATABLE_ARRAY.getData(asyncDataContext)?.singleOrNull()
          }
          else {
            null
          }
        } ?: return@launch

        if (project != null && Registry.`is`("ide.navigation.requests")) {
          val options = NavigationOptions.defaultOptions().requestFocus(false).preserveCaret(true).sourceNavigationOnly(true)
          project.serviceAsync<NavigationService>().navigate(navigatable, options)
        }
        else {
          withContext(Dispatchers.EDT) {
            SlowOperations.knownIssue("IDEA-304701, EA-677533").use {
              OpenSourceUtil.navigateToSource(false, true, navigatable)
            }
          }
        }
      }
  }
}