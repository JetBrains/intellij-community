// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.EntityTypesResolver

object SimpleEntityTypesResolver : EntityTypesResolver {
  override fun getPluginId(clazz: Class<*>): String? = null

  override fun resolveClass(name: String, pluginId: String?): Class<*> {
    if (name.startsWith("[")) return Class.forName(name)
    return this.javaClass.classLoader.loadClass(name)
  }
}