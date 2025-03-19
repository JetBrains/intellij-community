// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.i18n

import com.intellij.AbstractBundle
import fleet.util.multiplatform.Actual
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

/**
 * see expect function [com.intellij.platform.syntax.i18n.ResourceBundle]
 */
@Suppress("FunctionName")
@Actual("ResourceBundle")
internal fun ResourceBundleJvm(
  bundleClass: String,
  pathToBundle: String,
  self: Any,
): ResourceBundle {
  val bundleClazz = self.javaClass.classLoader.loadClass(bundleClass)
  val dynamicBundleClass = tryLoadDynamicBundle() ?: run {
    // IntelliJ Core is missing, falling back to com.intellij.AbstractBundle.AbstractBundle
    return createAbstractBundle(bundleClazz, pathToBundle)
  }

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

private fun createAbstractBundle(
  bundleClazz: Class<*>,
  pathToBundle: String,
): ResourceBundle {
  val abstractBundle = AbstractBundle(bundleClazz, pathToBundle)
  return object : ResourceBundle {
    override fun message(key: String, vararg params: Any): @Nls String {
      return abstractBundle.getMessage(key, *params)
    }

    override fun messagePointer(key: String, vararg params: Any): () -> @Nls String {
      return abstractBundle.getLazyMessage(key, *params)::get
    }
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