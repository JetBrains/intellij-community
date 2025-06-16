// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

abstract class ContentElement {
  class Module(
    val name: String,
    val loadingRule: ModuleLoadingRule = ModuleLoadingRule.OPTIONAL,
    val embeddedDescriptorContent: CharArray? = null,
  ) : ContentElement() {
    override fun toString(): String {
      return "Module(name=$name, loadingRule=$loadingRule)"
    }
  }
}