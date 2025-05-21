// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ExclusionClassLoader(val inner: ClassLoader, val filter: (fqn: String) -> Boolean): ClassLoader(inner) {
  override fun loadClass(name: String?): Class<*>? {
    if (!filter(name!!)) return null
    return super.loadClass(name)
  }

  override fun loadClass(name: String?, resolve: Boolean): Class<*>? {
    if (!filter(name!!)) return null
    return super.loadClass(name, resolve)
  }

  override fun findClass(name: String?): Class<*>? {
    if (!filter(name!!)) return null
    return super.findClass(name)
  }

  override fun findClass(moduleName: String?, name: String?): Class<*>? {
    if (!filter(name!!)) return null
    return super.findClass(moduleName, name)
  }
}