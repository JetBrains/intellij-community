// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.impl

import com.intellij.platform.workspace.storage.EntityTypesResolver

public object SimpleEntityTypesResolver : EntityTypesResolver {
  override fun getPluginId(clazz: Class<*>): String? = null

  override fun resolveClass(name: String, pluginId: String?): Class<*> {
    val classLoader = getClassLoader(pluginId) ?: error("Class loader isn't found")
    return loadClassByName(name, classLoader)
  }

  override fun getClassLoader(pluginId: String?): ClassLoader? {
    return javaClass.classLoader
  }
}