// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup

import com.intellij.openapi.application.ApplicationManager

abstract class PopupComponentFactoryService {
  companion object {
    @JvmStatic
    fun getInstance(): PopupComponentFactoryService = ApplicationManager.getApplication().getService(PopupComponentFactoryService::class.java)
  }

  abstract fun getDialogFactory(): PopupComponent.Factory
  abstract fun getHeavyweightFactory(): PopupComponent.Factory
  abstract fun getDefaultFactory(): PopupComponent.Factory
}