// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.testFramework

import com.intellij.platform.plugins.parser.impl.PluginDescriptorReaderContext
import com.intellij.platform.plugins.parser.impl.elements.OS
import com.intellij.util.xml.dom.NoOpXmlInterner
import com.intellij.util.xml.dom.XmlInterner

object ValidationPluginDescriptorReaderContext : PluginDescriptorReaderContext {
  override val interner: XmlInterner
    get() = NoOpXmlInterner
  override val elementOsFilter: (OS) -> Boolean
    get() = { true }
  override val isMissingIncludeIgnored: Boolean = false
}