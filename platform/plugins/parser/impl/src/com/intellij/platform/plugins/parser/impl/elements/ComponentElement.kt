// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

class ComponentElement(
  @JvmField val interfaceClass: String?,
  @JvmField val implementationClass: String?,
  @JvmField val headlessImplementationClass: String?,
  @JvmField val loadForDefaultProject: Boolean,
  @JvmField val os: OS?,
  @JvmField val overrides: Boolean,
  @JvmField val options: Map<String, String>
)