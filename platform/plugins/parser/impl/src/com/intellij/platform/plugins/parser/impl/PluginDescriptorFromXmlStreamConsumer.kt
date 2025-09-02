// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PluginDescriptorFromXmlStreamConsumer private constructor(
  val readContext: PluginDescriptorReaderContext,
  val xIncludeLoader: XIncludeLoader?,
  includeBase: String?,
) : PluginXmlStreamConsumer {
  constructor(
    readContext: PluginDescriptorReaderContext,
    xIncludeLoader: XIncludeLoader?,
  ) : this(readContext, xIncludeLoader, null)

  private val builder: PluginDescriptorBuilder = PluginDescriptorBuilderImpl()
  private val includeBaseStack = mutableListOf<String?>()

  init {
    if (includeBase != null) {
      includeBaseStack.add(includeBase)
    }
  }

  fun build(): RawPluginDescriptor = builder.build()

  fun getBuilder(): PluginDescriptorBuilder = builder

  override fun consume(reader: XMLStreamReader2) {
    readModuleDescriptor(
      consumer = this,
      reader = reader,
    )
  }

  internal val includeBase: String?
    get() = includeBaseStack.lastOrNull()

  internal fun pushIncludeBase(newBase: String?) {
    includeBaseStack.add(newBase)
  }

  internal fun popIncludeBase() {
    includeBaseStack.removeLast()
  }

  companion object {
    internal fun withIncludeBase(
      readContext: PluginDescriptorReaderContext,
      xIncludeLoader: XIncludeLoader?,
      includeBase: String?,
    ): PluginDescriptorFromXmlStreamConsumer = PluginDescriptorFromXmlStreamConsumer(readContext, xIncludeLoader, includeBase)
  }
}