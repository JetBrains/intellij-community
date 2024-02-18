// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.local

import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.platform.settings.DelegatedSettingsController
import com.intellij.platform.settings.GetResult
import com.intellij.platform.settings.SetResult
import com.intellij.platform.settings.SettingDescriptor

private class JsonMirrorController : DelegatedSettingsController {
  init {
    if (System.getProperty("idea.settings.json.mirror") == null) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?> {
    return GetResult.inapplicable()
  }

  override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): SetResult {
    return SetResult.INAPPLICABLE
  }
}