// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.util.KeyedLazyInstance

object ExtensionTestUtil {
  /**
   * @see ExtensionPointImpl.maskAll
   */
  @JvmOverloads
  @JvmStatic
  fun <T> maskExtensions(pointName: ExtensionPointName<T>,
                         newExtensions: List<T>,
                         parentDisposable: Disposable,
                         fireEvents: Boolean = true) {
    (pointName.point as ExtensionPointImpl<T>).maskAll(newExtensions, parentDisposable, fireEvents)
  }

  @JvmStatic
  fun <T, BEAN_TYPE : KeyedLazyInstance<T>, KeyT> addExtension(area: ExtensionsAreaImpl, collector: KeyedExtensionCollector<T, KeyT>, bean: BEAN_TYPE) {
    val point = area.getExtensionPoint<BEAN_TYPE>(collector.name)
    @Suppress("DEPRECATION")
    point.registerExtension(bean)
    collector.clearCache()
  }
}