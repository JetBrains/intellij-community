// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import com.intellij.util.xmlb.annotations.Attribute

/**
* Represents if the Kotlin plugin supports K1 or K2 mode. The following code to `plugin.xml` can be added to support both K1 and K2 modes:
 * ```xml
 * <extensions defaultExtensionNs="org.jetbrains.kotlin">
 *     <supportsKotlinPluginMode supportsK2="true"/>
 * </extensions>
 * ```
 * Or the following to support only K2 mode:
 * ```xml
 * <extensions defaultExtensionNs="org.jetbrains.kotlin">
 *     <supportsKotlinPluginMode supportsK1="false" supportsK2="true"/>
 * </extensions>
 * ```
 * To support only K1 mode (this is the default that will be used if no `<supportsKotlinPluginMode ... />` is specified):
 * ```xml
 * <extensions defaultExtensionNs="org.jetbrains.kotlin">
 *     <supportsKotlinPluginMode supportsK1="true" supportsK2="false"/>
 * </extensions>
 * ```
 */
class SupportsKotlinPluginMode {
  @Attribute("supportsK1")
  var supportsK1: Boolean = true

  @Attribute("supportsK2")
  var supportsK2: Boolean = false
}