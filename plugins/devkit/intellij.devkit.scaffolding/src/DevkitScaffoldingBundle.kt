// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("DevkitScaffoldingBundle")

package com.intellij.devkit.scaffolding

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.PropertyKey
import java.lang.invoke.MethodHandles
import java.util.function.Supplier

private const val BUNDLE: String = "messages.DevkitScaffoldingBundle"

private val ourInstance: DynamicBundle = DynamicBundle(MethodHandles.lookup().lookupClass(), BUNDLE)

fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: @NotNull Any?): @Nls String {
  return ourInstance.getMessage(key, params)
}

fun messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: @NotNull Any?): Supplier<String> {
  return ourInstance.getLazyMessage(key, params)
}
