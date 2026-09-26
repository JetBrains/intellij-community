// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.trace

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The raw-value mechanism's own guarantees, tested where the code lives.
 *
 * The exhaustive path table is `RequestPrivateDataTest` in AI Assistant, which also asserts that no collector there
 * declares a reserved name as a plain raw field. This covers what must not depend on any plugin being present: a
 * dangerous path yields no content at all, the reserved names cannot be declared away, and an IDE that cannot answer
 * the consent question records nothing.
 */
class TraceRawFieldsTest {
  @Test
  fun `content read from a dangerous path is not recorded`() {
    assertNull(checkFileContentForLogging("password.txt", "secret"))
    assertNull(checkFileContentForLogging(".ssh/id_rsa", "secret"))
    assertNull(checkFileContentForLogging("/tmp/prod.dump", "secret"))
  }

  @Test
  fun `content read from a safe path is recorded`() {
    assertNotNull(checkFileContentForLogging("src/Main.kt", "class Main"))
    assertFalse(isDangerousFileForLogging("src/Main.kt"))
  }

  @Test
  fun `one dangerous path in a set suppresses the whole value`() {
    assertNull(checkFileContentForLogging(listOf("src/Main.kt", "/tmp/password.txt"), "secret"))
    assertNotNull(checkFileContentForLogging(listOf("src/Main.kt", "docs/readme.md"), "class Main"))
  }

  @Test
  fun `absent content is never turned into a value`() {
    assertNull(checkFileContentForLogging("src/Main.kt", null))
    assertNull(checkFileContentForLogging(listOf("src/Main.kt"), null))
  }

  /**
   * The reserved names are the only thing that forces file-backed content through the checked field, so the list being
   * non-empty is asserted too: an emptied list would leave every case below passing while checking nothing.
   */
  @Test
  fun `a field name dedicated to file content cannot be declared as an unchecked raw field`() {
    assertTrue(reservedFileContentFieldNames.isNotEmpty(), "the reserved-name guard would be vacuous")

    for (fieldName in reservedFileContentFieldNames) {
      for (kind in TraceRawFieldKind.entries) {
        val error = assertThrows(IllegalArgumentException::class.java, { TraceRawField(fieldName, kind) }, fieldName)
        assertTrue(error.message.orEmpty().contains("TraceRawFileField"), fieldName)
      }
    }
  }

  @Test
  fun `raw logging is refused when no application can answer the consent question`() {
    assertFalse(TraceRawDataSharing.isRawDataLoggingAllowed())
    assertFalse(TraceRawDataSharing.isTraceCollectionAllowed())
  }
}
