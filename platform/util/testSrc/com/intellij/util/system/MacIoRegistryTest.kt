package com.intellij.util.system

import com.sun.jna.Native
import com.sun.jna.platform.mac.IOKitUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.MAC
import java.nio.charset.StandardCharsets.UTF_8

@EnabledOnOs(MAC)
class MacIoRegistryTest {
  @Test
  fun `platform data matches JNA`() {
    val properties = listOf("manufacturer", "model")
    val actual = MacIoRegistry.platformExpertData(properties)
    val service = IOKitUtil.getMatchingService("IOPlatformExpertDevice")
    assertThat(service).isNotNull()
    try {
      for (property in properties) {
        val bytes = service.getByteArrayProperty(property)
        assertThat(actual[property]).isEqualTo(bytes?.let { Native.toString(it, UTF_8) })
      }
      assertThat(actual["model"]).isNotBlank()
    }
    finally {
      service.release()
    }
  }

  @Test
  fun `missing and nondata properties are omitted`() {
    assertThat(MacIoRegistry.platformExpertData(listOf("IntellijMissingProperty", "IOPlatformUUID"))).isEmpty()
  }
}
