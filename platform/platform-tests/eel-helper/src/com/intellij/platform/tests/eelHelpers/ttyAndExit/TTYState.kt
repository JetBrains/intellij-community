// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tests.eelHelpers.ttyAndExit

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.jetbrains.annotations.TestOnly

@TestOnly
data class TTYState(val size: Size?, val termName: String?) {
  companion object {
    private val mapper = ObjectMapper().registerKotlinModule()

    // Remove spaces, tabs, ANSI ESC another tty junk
    private val cleaner = Regex("([\\t\\n \\r\\s]|\\[[0-9;]*[a-zA-Z])")

    @TestOnly
    fun deserialize(str: String): TTYState = mapper.readValue(str, TTYState::class.java)

    @TestOnly
    fun deserializeIfValid(str: String): TTYState? = try {
      deserialize(cleaner.replace(str.trim(), ""))
    }
    catch (_: JsonProcessingException) {
      null
    }
    catch (_: JsonParseException) {
      null
    }
  }

  @TestOnly
  fun serialize(): String = mapper.writeValueAsString(this)
}