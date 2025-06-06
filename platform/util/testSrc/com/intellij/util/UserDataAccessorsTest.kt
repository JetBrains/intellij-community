// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.userData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

class UserDataAccessorsTest {
  @Test
  fun simple() {
    val holder = UserDataHolderBase()
    assertNull(holder.foo)
    holder.foo = "bar"
    assertEquals("bar", holder.foo)
  }
  
  @Test
  fun `with default value`() {
    val holder = UserDataHolderBase()
    assertEquals("bar", holder.bar)
    holder.bar = "baz"
    assertEquals("baz", holder.bar)
    
    val holder2 = UserDataHolderBase()
    holder2.bar = "baz"
    assertEquals("baz", holder2.bar)
  }
}

private var UserDataHolderBase.foo: String? by userData()
private var UserDataHolderBase.bar: String by userData { "bar" }
