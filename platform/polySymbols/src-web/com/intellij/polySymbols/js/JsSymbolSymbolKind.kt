// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JsSymbolSymbolKind")

package com.intellij.polySymbols.js

/**
 * [JS_SYMBOLS] symbol kinds.
 */
enum class JsSymbolSymbolKind {
  Variable,
  Function,
  Namespace,
  Class,
  Interface,
  Property,
  Method,
  Enum,
  Alias,
  Module,
  Type,
  ObjectLiteral,
}