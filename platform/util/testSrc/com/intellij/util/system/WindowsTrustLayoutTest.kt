// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.ValueLayout.ADDRESS

/**
 * Pins the ABI of the structures of [WindowsTrust] on every operating system.
 *
 * A layout is plain data and loads no library, so the test needs no Windows.
 * The sizes and the offsets are the ones of `wintrust.h` and `wincrypt.h` for a 64-bit target.
 */
internal class WindowsTrustLayoutTest {
  @Test
  fun `the sizes are the 64-bit ones`() {
    assertThat(ADDRESS.byteSize()).isEqualTo(8L)
  }

  @Test
  fun `WINTRUST_FILE_INFO`() {
    assertThat(WindowsTrust.WINTRUST_FILE_INFO.byteSize()).isEqualTo(32L)
    assertThat(WindowsTrust.WINTRUST_FILE_INFO.byteOffset(groupElement("cbStruct"))).isEqualTo(0L)
    assertThat(WindowsTrust.FILE_INFO_FILE_PATH).isEqualTo(8L)
  }

  @Test
  fun `WINTRUST_DATA`() {
    // WinVerifyTrust reads `cbStruct`, so the size must be the one of the full structure.
    assertThat(WindowsTrust.WINTRUST_DATA.byteSize()).isEqualTo(88L)
    assertThat(WindowsTrust.WINTRUST_DATA.byteOffset(groupElement("cbStruct"))).isEqualTo(0L)
    assertThat(WindowsTrust.DATA_UI_CHOICE).isEqualTo(24L)
    assertThat(WindowsTrust.DATA_REVOCATION_CHECKS).isEqualTo(28L)
    assertThat(WindowsTrust.DATA_UNION_CHOICE).isEqualTo(32L)
    assertThat(WindowsTrust.DATA_FILE).isEqualTo(40L)
    assertThat(WindowsTrust.DATA_STATE_ACTION).isEqualTo(48L)
    assertThat(WindowsTrust.DATA_STATE_DATA).isEqualTo(56L)
    assertThat(WindowsTrust.DATA_PROV_FLAGS).isEqualTo(72L)
  }

  @Test
  fun `CRYPT_PROVIDER_CERT`() {
    assertThat(WindowsTrust.CRYPT_PROVIDER_CERT.byteSize()).isEqualTo(16L)
    assertThat(WindowsTrust.PROVIDER_CERT_CERT).isEqualTo(8L)
  }

  @Test
  fun `CERT_CONTEXT`() {
    assertThat(WindowsTrust.CERT_CONTEXT.byteSize()).isEqualTo(40L)
    assertThat(WindowsTrust.CERT_CONTEXT_ENCODED).isEqualTo(8L)
    assertThat(WindowsTrust.CERT_CONTEXT_ENCODED_SIZE).isEqualTo(16L)
  }
}
