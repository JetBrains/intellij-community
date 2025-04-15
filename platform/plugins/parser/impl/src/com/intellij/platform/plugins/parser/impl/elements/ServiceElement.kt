// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

class ServiceElement(
  @JvmField val serviceInterface: String?,
  @JvmField val serviceImplementation: String?,
  @JvmField val testServiceImplementation: String?,
  @JvmField val headlessImplementation: String?,
  @JvmField val overrides: Boolean,
  @JvmField val configurationSchemaKey: String?,
  @JvmField val preload: PreloadMode,
  @JvmField val client: ClientKind?,
  @JvmField val os: OS?
)
