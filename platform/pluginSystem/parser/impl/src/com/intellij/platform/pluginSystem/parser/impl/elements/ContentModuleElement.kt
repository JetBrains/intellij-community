// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.parser.impl.elements

class ContentModuleElement(
  val name: String,
  /** The explicit namespace of the `<content>` block, or null for the implicit namespace of the plugin. */
  val namespace: String?,
  val loadingRule: ModuleLoadingRuleValue,
  /** `required-if-available` attribute value */
  val requiredIfAvailable: String?,
  val embeddedDescriptorContent: CharArray?,
) {
  override fun toString(): String {
    return "Module(name=$name, loadingRule=$loadingRule)"
  }
}