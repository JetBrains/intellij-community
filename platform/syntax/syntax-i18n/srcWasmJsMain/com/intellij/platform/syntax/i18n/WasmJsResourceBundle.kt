// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.i18n

import com.intellij.AbstractBundle
import fleet.util.multiplatform.Actual
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

// TODO implement Gradle plugin which takes a .properties file and generates a corresponding kotlin class

/**
 * see expect function [com.intellij.platform.syntax.i18n.ResourceBundle]
 */
@Actual("ResourceBundle")
internal fun ResourceBundleWasmJs(bundleClass: String, pathToBundle: String, self: Any): ResourceBundle =
  DummyResourceBundle

@Suppress("HardCodedStringLiteral")
private object DummyResourceBundle : ResourceBundle {
  override fun message(key: String, vararg params: Any): @Nls String = key
  override fun messagePointer(key: String, vararg params: Any): () -> @Nls String = { key }
}
