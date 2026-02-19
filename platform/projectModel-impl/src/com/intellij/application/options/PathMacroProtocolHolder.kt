// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import java.util.concurrent.atomic.AtomicBoolean

internal object PathMacroProtocolHolder {
  @JvmStatic
  val protocols: MutableSet<String> = ConcurrentCollectionFactory.createConcurrentSet()

  private val loadedAppExtensions = AtomicBoolean()

  init {
    protocols.add("file")
    protocols.add("jar")
    protocols.add("jrt")
  }

  @JvmStatic
  internal fun loadAppExtensions(app: Application) {
    if (loadedAppExtensions.compareAndSet(false, true)) {
      if (ApplicationManager.getApplication().getExtensionArea().hasExtensionPoint(PathMacroExpandableProtocolBean.EP_NAME)) {
        PathMacroExpandableProtocolBean.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<PathMacroExpandableProtocolBean> {
          override fun extensionAdded(extension: PathMacroExpandableProtocolBean, pluginDescriptor: PluginDescriptor) {
            protocols.add(extension.protocol)
          }

          override fun extensionRemoved(extension: PathMacroExpandableProtocolBean, pluginDescriptor: PluginDescriptor) {
            protocols.remove(extension.protocol)
          }
        })
        PathMacroExpandableProtocolBean.EP_NAME.forEachExtensionSafe { bean: PathMacroExpandableProtocolBean ->
          protocols.add(bean.protocol)
        }
      }
    }
  }
}