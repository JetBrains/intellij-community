// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.i18n

import fleet.util.multiplatform.Actual

/**
 * see expect function [com.intellij.platform.syntax.i18n.ResourceBundle]
 */
@Actual("ResourceBundle")
internal fun ResourceBundleJs(
  bundleClass: String,
  pathToBundle: String,
  self: Any,
  defaultMapping: Map<String, String>,
): ResourceBundle = BaseResourceBundle(defaultMapping)
