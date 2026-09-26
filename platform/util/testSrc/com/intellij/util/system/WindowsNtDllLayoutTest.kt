// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.ValueLayout.ADDRESS

/**
 * Pins the ABI of the NT structures of [WindowsNtDll] on every operating system.
 *
 * A layout is plain data and loads no library, so the test needs no Windows. It replaces the field order that JNA
 * used to derive at run time. The test must not read the capture-state layout, because that one holds `errno`
 * instead of `GetLastError` on Linux.
 */
internal class WindowsNtDllLayoutTest {
  @Test
  fun `the sizes are the 64-bit ones`() {
    assertThat(ADDRESS.byteSize()).isEqualTo(8L)
  }

  @Test
  fun `UNICODE_STRING`() {
    assertThat(WindowsNtDll.UNICODE_STRING.byteSize()).isEqualTo(16L)
    assertThat(WindowsNtDll.UNICODE_STRING.byteOffset(groupElement("Length"))).isEqualTo(0L)
    assertThat(WindowsNtDll.UNICODE_STRING.byteOffset(groupElement("MaximumLength"))).isEqualTo(2L)
    assertThat(WindowsNtDll.UNICODE_STRING.byteOffset(groupElement("Buffer"))).isEqualTo(8L)

    assertThat(WindowsNtDll.UNICODE_STRING_LENGTH).isEqualTo(0L)
    assertThat(WindowsNtDll.UNICODE_STRING_BUFFER).isEqualTo(8L)
  }

  @Test
  fun `IO_STATUS_BLOCK`() {
    assertThat(WindowsNtDll.IO_STATUS_BLOCK.byteSize()).isEqualTo(16L)
    assertThat(WindowsNtDll.IO_STATUS_BLOCK.byteOffset(groupElement("Pointer"))).isEqualTo(0L)
    assertThat(WindowsNtDll.IO_STATUS_BLOCK.byteOffset(groupElement("Information"))).isEqualTo(8L)
  }

  @Test
  fun `PROCESS_BASIC_INFORMATION`() {
    // NtQueryInformationProcess answers STATUS_INFO_LENGTH_MISMATCH for a buffer shorter than 48 bytes.
    assertThat(WindowsNtDll.PROCESS_BASIC_INFORMATION.byteSize()).isEqualTo(48L)
    assertThat(WindowsNtDll.PEB_BASE_ADDRESS).isEqualTo(8L)
    assertThat(WindowsNtDll.INHERITED_FROM_UNIQUE_PROCESS_ID).isEqualTo(40L)
  }

  @Test
  fun `PEB`() {
    assertThat(WindowsNtDll.PEB.byteSize()).isEqualTo(40L)
    assertThat(WindowsNtDll.PROCESS_PARAMETERS).isEqualTo(32L)
  }

  @Test
  fun `RTL_USER_PROCESS_PARAMETERS`() {
    assertThat(WindowsNtDll.RTL_USER_PROCESS_PARAMETERS.byteSize()).isEqualTo(128L)
    assertThat(WindowsNtDll.IMAGE_PATH_NAME).isEqualTo(96L)
    assertThat(WindowsNtDll.COMMAND_LINE).isEqualTo(112L)

    // The embedded UNICODE_STRING must compose at the right offset, because readRemoteString depends on it.
    val commandLineBuffer = WindowsNtDll.RTL_USER_PROCESS_PARAMETERS.byteOffset(groupElement("CommandLine"), groupElement("Buffer"))
    assertThat(commandLineBuffer).isEqualTo(120L)
  }

  @Test
  fun `FILE_PROCESS_IDS_USING_FILE_INFORMATION grows by one entry of 8 bytes`() {
    assertThat(WindowsNtDll.FILE_PROCESS_IDS.byteSize()).isEqualTo(16L)
    assertThat(WindowsNtDll.fileProcessIds(16).byteSize()).isEqualTo(136L)

    assertThat(WindowsNtDll.NUMBER_OF_PROCESS_IDS_IN_LIST).isEqualTo(0L)
    assertThat(WindowsNtDll.PROCESS_ID_LIST).isEqualTo(8L)
    assertThat(WindowsNtDll.fileProcessIds(16).byteOffset(groupElement("ProcessIdList"))).isEqualTo(8L)
  }

  @Test
  fun `the NT constants`() {
    assertThat(WindowsNtDll.FILE_PROCESS_IDS_CLASS).isEqualTo(47)
    assertThat(WindowsNtDll.PROCESS_BASIC_INFORMATION_CLASS).isEqualTo(0)
    assertThat(WindowsNtDll.STATUS_INFO_LENGTH_MISMATCH).isEqualTo(-1073741820)
  }
}
