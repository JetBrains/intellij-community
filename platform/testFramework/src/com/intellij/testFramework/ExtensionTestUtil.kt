// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.util.KeyedLazyInstance
import java.util.function.Predicate

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
    (pointName.getPoint(null) as ExtensionPointImpl<T>).maskAll(newExtensions, parentDisposable, fireEvents)
  }

  @JvmStatic
  fun <T> addExtension(area: ExtensionsAreaImpl, collector: LanguageExtension<T>, language: Language, instance: T) {
    addExtension(area, collector, language.id, instance)
  }

  @JvmStatic
  fun <T, KeyT> addExtension(area: ExtensionsAreaImpl, collector: KeyedExtensionCollector<T, KeyT>, key: String, instance: T) {
    val point = area.getExtensionPoint<KeyedLazyInstance<T>>(collector.name)

    if (ApplicationManager.getApplication() !is MockApplication) {
      // if not MockApplication, it means that extension point is not registered as fake bean
      ExtensionPointImpl.setTestTypeChecker(Predicate { KeyedLazyInstance::class.java.isAssignableFrom(it) }, ApplicationManager.getApplication())
    }

    @Suppress("DEPRECATION")
    point.registerExtension(object : KeyedLazyInstance<T> {
      override fun getKey() = key

      override fun getInstance() = instance
    })
    collector.clearCache()
  }
}