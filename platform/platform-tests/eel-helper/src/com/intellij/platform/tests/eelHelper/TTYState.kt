// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tests.eelHelper

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.jetbrains.annotations.TestOnly

@TestOnly
data class TTYState(val size: Size?) {
  companion object {
    private val mapper = ObjectMapper().registerKotlinModule()

    @TestOnly
    fun deserialize(str: String): TTYState = mapper.readValue(str, TTYState::class.java)
  }

  @TestOnly
  fun serialize(): String = mapper.writeValueAsString(this)
}