// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserDataHolderExTest {

  @Test
  fun getOrCreateUserData() {
    val key = Key<Int>("a")
    val userData = UserDataHolderBase()
    assertEquals(42, userData.getOrCreateUserData(key) { 42 })
    assertEquals(42, userData.getOrCreateUserData(key) { 239 })

    userData.putUserData(key, null)
    assertEquals(2411, userData.getOrCreateUserData(key, { 2411 }))
    assertEquals(2411, userData.getUserData(key))
  }

  @Test
  fun getOrCreateUserDataNullable() {
    val key = Key<Int>("a")
    val userData = UserDataHolderBase()
    assertEquals(null, userData.getOrMaybeCreateUserData(key) { null })
    assertEquals(42, userData.getOrCreateUserData(key) { 42 })
    assertEquals(42, userData.getOrMaybeCreateUserData(key) { null })
  }

  @Test
  fun updateUserData() {
    val key = Key<Int>("a")
    val userData = UserDataHolderBase()
    userData.updateUserData(key) { 1 }
    assertEquals(1, userData.getUserData(key))

    assertEquals(2, userData.updateUserData(key) { value ->
      (value ?: 0).plus(1)
    })

    assertEquals(null, userData.updateUserData(key) { null })
    assertEquals(null, userData.getUserData(key))
  }

  @Test
  fun getAndUpdateUserData() {
    val key = Key<Int>("a")
    val userData = UserDataHolderBase()
    assertEquals(null, userData.getAndUpdateUserData(key) { 1 })
    assertEquals(1, userData.getAndUpdateUserData(key) { 2 })
    assertEquals(2, userData.getUserData(key))
    assertEquals(2, userData.getAndUpdateUserData(key) { it!! + 1 })
    assertEquals(3, userData.getUserData(key))
    assertEquals(3, userData.getAndUpdateUserData(key) { null })
    assertEquals(null, userData.getUserData(key))
  }
}

