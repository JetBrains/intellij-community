// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.callUnderOwnClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * An index ID keeps the top frames of the code that registered it as data. The ID lives as long as the application,
 * and it keeps no class loader of the code that registered it.
 */
internal class IdRegistrationTraceTest {
  @Test
  fun `an id keeps no class loader of the code that registered it`() {
    val call = callUnderOwnClassLoader { ID.create<String, String>(ID_NAME) }
    try {
      call.loader.ensureCollected()
      assertThat(call.result.registrationTrace.stackTrace.map { it.className }).contains(OWN_CLASS_LOADER_CALLER)
    }
    finally {
      ID.unloadId(call.result)
    }
  }

  @Test
  fun `the registration trace starts at the code that creates the id, below the id classes, and keeps 32 frames`() {
    val id = createId()
    try {
      val frames = id.registrationTrace.stackTrace

      assertThat(frames.first().methodName).isEqualTo("createId")
      assertThat(frames).hasSize(32)
    }
    finally {
      ID.unloadId(id)
    }
  }

  @Test
  fun `an access from another plugin names the registration trace and carries it as the cause`() {
    val id = createId()
    try {
      assertThatThrownBy { id.findForPlugin(PluginId.getId("IdRegistrationTraceTest.other")) }
        .isInstanceOf(AssertionError::class.java)
        .hasMessageEndingWith(" Registration stack trace: ")
        .cause().isNotNull()
    }
    finally {
      ID.unloadId(id)
    }
  }

  private fun createId(): RegistrationTraceTestId = RegistrationTraceTestId(SUBCLASS_ID_NAME)
}

/** An ID class of its own, so the constructor of a subclass of ID is on the stack of the registration. */
private class RegistrationTraceTestId(name: String) : ID<String, String>(name, null) {
  fun findForPlugin(pluginId: PluginId): ID<String, String>? = findByName(name, true, pluginId)
}

private const val ID_NAME = "IdRegistrationTraceTest.id"
private const val SUBCLASS_ID_NAME = "IdRegistrationTraceTest.subclass"
private const val OWN_CLASS_LOADER_CALLER = "com.intellij.testFramework.OwnClassLoaderCaller"
