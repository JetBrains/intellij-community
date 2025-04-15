// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.i18n

import fleet.util.multiplatform.Actual
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

/**
 * see expect function [com.intellij.platform.syntax.i18n.ResourceBundle]
 */
@Suppress("FunctionName", "unused")
@Actual("ResourceBundle")
internal fun ResourceBundleJvm(
  bundleClass: String,
  pathToBundle: String,
  self: Any,
  defaultMapping: Map<String, String>,
): ResourceBundle {
  val dynamicBundleClass = tryLoadDynamicBundle() ?: return BaseResourceBundle(defaultMapping)
  val bundleClazz = self.javaClass.classLoader.loadClass(bundleClass)
  return createDynamicBundle(dynamicBundleClass, bundleClazz, pathToBundle)
}

private fun tryLoadDynamicBundle(): Class<*>? {
  return try {
    Class.forName(dynamicBundleFQN)
  }
  catch (_: ClassNotFoundException) {
    null
  }
}

private fun createDynamicBundle(
  dynamicBundleClass: Class<*>,
  bundleClazz: Class<*>,
  pathToBundle: String,
): ResourceBundle {
  val constructor = dynamicBundleClass.getConstructor(Class::class.java, String::class.java)
  val bundle = constructor.newInstance(bundleClazz, pathToBundle)
  val getMessageMethod = dynamicBundleClass.getMethod("getMessage", String::class.java, Array<Any>::class.java)
  val getLazyMessageMethod = dynamicBundleClass.getMethod("getLazyMessage", String::class.java, Array<Any>::class.java)
  return object : ResourceBundle {
    override fun message(key: String, vararg params: Any): String {
      return getMessageMethod.invoke(bundle, key, params) as String
    }

    override fun messagePointer(key: String, vararg params: Any): () -> String {
      @Suppress("UNCHECKED_CAST")
      val lazyMessage = getLazyMessageMethod.invoke(bundle, key, params) as Supplier<String>
      return lazyMessage::get
    }
  }
}

private const val dynamicBundleFQN: String = "com.intellij.DynamicBundle"