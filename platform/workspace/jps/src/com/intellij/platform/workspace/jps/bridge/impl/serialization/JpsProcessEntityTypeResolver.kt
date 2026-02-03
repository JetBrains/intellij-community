// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.serialization

import com.intellij.platform.workspace.storage.EntityTypesResolver

/**
 * An implementation of [EntityTypesResolver] used inside JPS build process where all classes are loaded by the same classloader.
 */
internal class JpsProcessEntityTypeResolver : EntityTypesResolver {
  override fun getPluginIdAndModuleId(clazz: Class<*>): Pair<String?, String?> {
    return null to null
  }

  override fun resolveClass(name: String, pluginId: String?, moduleId: String?): Class<*> {
    return Class.forName(name, true, javaClass.classLoader)
  }

  override fun getClassLoader(pluginId: String?, moduleId: String?): ClassLoader? {
    return javaClass.classLoader
  }
}
