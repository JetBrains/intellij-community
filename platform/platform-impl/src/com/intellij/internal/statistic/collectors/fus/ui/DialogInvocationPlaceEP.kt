// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute

/**
 * Extension point to register an invocation place ID for a dialog to record in feature usage statistics.
 */
internal class DialogInvocationPlaceEP {
  @Attribute("id")
  @RequiredElement
  var id: String? = null
}
