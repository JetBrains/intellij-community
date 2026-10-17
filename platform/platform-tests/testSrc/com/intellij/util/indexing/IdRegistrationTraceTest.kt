// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.testFramework.callUnderOwnClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * An index ID keeps the stack that registered it as data. The ID lives as long as the application,
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
}

private const val ID_NAME = "IdRegistrationTraceTest.id"
private const val OWN_CLASS_LOADER_CALLER = "com.intellij.testFramework.OwnClassLoaderCaller"
