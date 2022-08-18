// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.AreaInstance
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.util.KeyedLazyInstance
import org.jetbrains.annotations.TestOnly

@TestOnly
object ExtensionTestUtil {

  /**
   * @see ExtensionPointImpl.maskAll
   */
  @JvmOverloads
  @JvmStatic
  fun <T : Any> maskExtensions(
    pointName: ExtensionPointName<T>,
    newExtensions: List<T>,
    parentDisposable: Disposable,
    fireEvents: Boolean = true,
    areaInstance: AreaInstance? = null,
  ) {
    (pointName.getPoint(areaInstance) as ExtensionPointImpl<T>).maskAll(newExtensions, parentDisposable, fireEvents)
  }

  /**
   * Takes current extensions for the extension point,
   * adds a given extensions and masks the extension point
   * with the resulting list of extensions.
   *
   * @see ExtensionPointImpl.maskAll
   */
  @JvmStatic
  @JvmOverloads
  fun <T : Any> addExtensions(
    pointName: ExtensionPointName<T>,
    extensionsToAdd: List<T>,
    parentDisposable: Disposable,
    fireEvents: Boolean = true,
    areaInstance: AreaInstance? = null,
  ) {
    maskExtensions(pointName, pointName.extensionList + extensionsToAdd, parentDisposable, fireEvents, areaInstance)
  }

  @JvmStatic
  fun <T, BEAN_TYPE : KeyedLazyInstance<T>, KeyT> addExtension(area: ExtensionsAreaImpl, collector: KeyedExtensionCollector<T, KeyT>, bean: BEAN_TYPE) {
    val point = area.getExtensionPoint<BEAN_TYPE>(collector.name)
    @Suppress("DEPRECATION")
    point.registerExtension(bean)
    collector.clearCache()
  }
}
