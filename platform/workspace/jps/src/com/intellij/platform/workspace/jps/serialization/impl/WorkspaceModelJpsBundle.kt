// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.AbstractBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

internal object WorkspaceModelJpsBundle {
  private const val BUNDLE_NAME = "messages.WorkspaceModelJpsBundle"
  private val BUNDLE = loadDynamicBundle()

  private fun loadDynamicBundle(): AbstractBundle {
    val dynamicBundleClass = 
      try {
        Class.forName("com.intellij.DynamicBundle")
      }
      catch (e: ClassNotFoundException) {
        try {
          Class.forName("org.jetbrains.jps.api.JpsDynamicBundle")
        }
        catch (e: ClassNotFoundException) {
          error("This class is supposed to be used either from IDE process, or from JPS build process, but neither DynamicBundle, nor JpsDynamicBundle cannot be found")
        }
      }
    val constructor = dynamicBundleClass.getConstructor(Class::class.java, String::class.java)
    return constructor.newInstance(WorkspaceModelJpsBundle::class.java, BUNDLE_NAME) as AbstractBundle
  }

  @JvmStatic
  fun message(key: @PropertyKey(resourceBundle = BUNDLE_NAME) String, vararg params: Any): @Nls String {
    return BUNDLE.getMessage(key, *params)
  }
}
