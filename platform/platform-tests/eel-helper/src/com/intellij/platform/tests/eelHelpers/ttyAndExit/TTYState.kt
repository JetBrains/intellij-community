// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.tests.eelHelpers.ttyAndExit

import org.jetbrains.annotations.TestOnly
import tools.jackson.core.JacksonException
import tools.jackson.core.exc.StreamReadException
import tools.jackson.module.kotlin.jacksonObjectMapper

@TestOnly
data class TTYState(val size: Size?, val termName: String?) {
  companion object {
    private val mapper = jacksonObjectMapper()

    @TestOnly
    private fun deserialize(str: String): TTYState = mapper.readValue(str, TTYState::class.java)

    @TestOnly
    fun deserializeIfValid(str: String, onError:(message:String)-> Unit): TTYState? = try {
      deserialize(str.trim())
    }
    catch (e: StreamReadException) {
      onError("Can't parse due to ${e.message}")
      null
    }
    catch (_: JacksonException) {
      null
    }
  }

  @TestOnly
  fun serialize(): String = mapper.writeValueAsString(this)
}