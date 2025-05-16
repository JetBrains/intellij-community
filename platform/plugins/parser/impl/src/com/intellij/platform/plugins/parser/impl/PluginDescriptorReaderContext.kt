// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import com.intellij.platform.plugins.parser.impl.elements.OS
import com.intellij.util.xml.dom.XmlInterner
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PluginDescriptorReaderContext {
  val interner: XmlInterner

  val elementOsFilter: (OS) -> Boolean

  val isMissingIncludeIgnored: Boolean
}