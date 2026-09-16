package com.intellij.util.system

import com.sun.jna.platform.win32.COM.WbemcliUtil
import com.sun.jna.platform.win32.Ole32
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.WINDOWS
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@EnabledOnOs(WINDOWS)
class WindowsWmiTest {
  private enum class ComputerSystemProperty { Manufacturer, Model, NumberOfLogicalProcessors, HypervisorPresent }

  @Test
  fun `computer system values match JNA`() {
    onFreshThread {
      val actual = WindowsWmi.query("ROOT\\CIMV2", "Win32_ComputerSystem", ComputerSystemProperty.entries.map { it.name }, 10_000)
      assertThat(actual).hasSize(1)
      assertThat(Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED).toInt()).isZero()
      try {
        val expected = WbemcliUtil.WmiQuery("Win32_ComputerSystem", ComputerSystemProperty::class.java).execute(10_000)
        assertThat(expected.resultCount).isEqualTo(actual.size)
        for (property in ComputerSystemProperty.entries) {
          assertThat(actual.single()[property.name]).isEqualTo(expected.getValue(property, 0))
        }
        assertThat(actual.single()["Manufacturer"]).isInstanceOf(String::class.java)
        assertThat(actual.single()["NumberOfLogicalProcessors"]).isInstanceOf(Int::class.javaObjectType)
        assertThat(actual.single()["HypervisorPresent"]).isInstanceOf(Boolean::class.javaObjectType)
      }
      finally {
        Ole32.INSTANCE.CoUninitialize()
      }
    }
  }

  @Test
  fun `query preserves an existing multithreaded apartment`() {
    onFreshThread {
      assertThat(Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED).toInt()).isZero()
      try {
        repeat(3) {
          assertThat(WindowsWmi.query("ROOT\\CIMV2", "Win32_ComputerSystem", listOf("Model"), 10_000)).hasSize(1)
        }
        assertThat(Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED).toInt()).isEqualTo(0x80010106.toInt())
      }
      finally {
        Ole32.INSTANCE.CoUninitialize()
      }
      assertFreshApartment()
    }
  }

  @Test
  fun `query balances initialization in an existing single threaded apartment`() {
    onFreshThread {
      assertThat(Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED).toInt()).isZero()
      try {
        repeat(3) {
          assertThat(WindowsWmi.query("ROOT\\CIMV2", "Win32_ComputerSystem", listOf("Model"), 10_000)).hasSize(1)
        }
      }
      finally {
        Ole32.INSTANCE.CoUninitialize()
      }
      assertFreshApartment()
    }
  }

  @Test
  fun `invalid namespace preserves the error code and releases COM`() {
    onFreshThread {
      assertThatThrownBy {
        WindowsWmi.query("ROOT\\IntellijMissingNamespace", "Win32_ComputerSystem", listOf("Model"), 10_000)
      }.isInstanceOfSatisfying(WindowsComException::class.java) {
        assertThat(it.hresult).isEqualTo(0x8004100E.toInt())
      }
      assertFreshApartment()
    }
  }

  private fun assertFreshApartment() {
    val result = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED)
    try {
      assertThat(result.toInt()).isZero()
    }
    finally {
      if (result.toInt() >= 0) Ole32.INSTANCE.CoUninitialize()
    }
  }

  private fun onFreshThread(action: () -> Unit) {
    Executors.newSingleThreadExecutor(Thread.ofPlatform().name("WMI test").factory()).use { executor ->
      executor.submit(action).get(60, TimeUnit.SECONDS)
    }
  }
}
