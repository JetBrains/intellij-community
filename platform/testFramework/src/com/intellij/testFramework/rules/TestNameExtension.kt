// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.rules

import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.lang.reflect.Method
import kotlin.properties.Delegates

class TestNameExtension : BeforeEachCallback {
  var methodName: String by Delegates.notNull()
  var displayName: String by Delegates.notNull()


  @Throws(Exception::class)
  override fun beforeEach(context: ExtensionContext) {
    methodName = context.testMethod.map { m: Method -> m.name }.orElse(context.displayName)
    displayName = context.displayName
  }
}