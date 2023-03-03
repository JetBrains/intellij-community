// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup


class LocalPopupComponentFactoryService: PopupComponentFactoryService() {
  override fun getDialogFactory(): PopupComponent.Factory {
    return PopupComponent.Factory.Dialog()
  }

  override fun getHeavyweightFactory(): PopupComponent.Factory {
    return PopupComponent.Factory.AwtHeavyweight()
  }

  override fun getDefaultFactory(): PopupComponent.Factory {
    return PopupComponent.Factory.AwtDefault()
  }
}