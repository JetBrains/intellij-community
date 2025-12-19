// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer


internal interface ConstructWithFactoryMethod {
  companion object {
    @JvmStatic
    fun createServiceInstance(): Any {
      return object : ConstructWithFactoryMethod {}
    }

    @JvmStatic
    fun createServiceInstance(foo: Boolean): Any {
      throw AssertionError("Should not be invoked")
    }
  }
}
