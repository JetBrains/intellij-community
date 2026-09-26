// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import com.intellij.util.system.WindowsRegistry.Hive
import com.intellij.util.system.WindowsRegistry.Key
import com.intellij.util.system.WindowsRegistry.RegistryException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.WINDOWS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile

/** Reads well-known keys through a [Key]. The keys exist on every supported Windows, so the test needs no fixture. */
@EnabledOnOs(WINDOWS)
internal class WindowsRegistryKeyTest {
  private val windowsNt = "SOFTWARE\\Microsoft\\Windows NT"
  private val currentVersion = "$windowsNt\\CurrentVersion"

  @Test
  fun `reads a string value`() {
    Key.open(Hive.LOCAL_MACHINE, currentVersion).use { key ->
      assertThat(key).isNotNull
      assertThat(key!!.getString("ProductName")).startsWith("Windows")
      assertThat(key.getString("no such value")).isNull()
    }
  }

  @Test
  fun `lists the subkeys`() {
    Key.open(Hive.LOCAL_MACHINE, windowsNt).use { key ->
      assertThat(key!!.subKeys()).contains("CurrentVersion")
      assertThat(key.exists("CurrentVersion")).isTrue
      assertThat(key.exists("no such key")).isFalse
      key.open("CurrentVersion").use { child ->
        assertThat(child!!.getString("ProductName")).startsWith("Windows")
      }
      assertThat(key.open("no such key")).isNull()
    }
  }

  @Test
  fun `maps the value types`() {
    Key.open(Hive.LOCAL_MACHINE, currentVersion).use { key ->
      val values = key!!.values()
      assertThat(values["ProductName"]).isInstanceOf(String::class.java).isEqualTo(key.getString("ProductName"))
      assertThat(values["CurrentMajorVersionNumber"]).isInstanceOf(Int::class.javaObjectType)
      assertThat(values["InstallTime"]).isInstanceOf(Long::class.javaObjectType)
      assertThat(values["DigitalProductId"]).isInstanceOf(ByteArray::class.java)
    }
  }

  @Test
  fun `does not expand a REG_EXPAND_SZ value`() {
    Key.open(Hive.LOCAL_MACHINE, "SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment").use { key ->
      val temp = key!!.getString("TEMP")
      assertThat(temp).contains("%")
      assertThat(key.values()["TEMP"]).isEqualTo(temp)
    }
  }

  @Test
  fun `an empty path opens the hive itself`() {
    Key.open(Hive.CURRENT_USER, "").use { root ->
      assertThat(root!!.subKeys()).contains("Software")
      assertThat(root.exists("Software")).isTrue
    }
  }

  @Test
  fun `a missing key is null`() {
    assertThat(Key.open(Hive.CURRENT_USER, "SOFTWARE\\JetBrains\\no such key")).isNull()
  }

  @Test
  fun `a closed key rejects a read`() {
    val key = Key.open(Hive.LOCAL_MACHINE, currentVersion)!!
    key.close()
    key.close()
    assertThatThrownBy { key.subKeys() }.isInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun `a missing hive file reports ERROR_FILE_NOT_FOUND`(@TempDir tempDir: Path) {
    assertThatThrownBy { Key.loadAppKey(tempDir.resolve("privateregistry.bin")) }
      .isInstanceOf(RegistryException::class.java)
      .matches { (it as RegistryException).errorCode == 2 }
  }

  @Test
  fun `a file that is not a hive is an error`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("privateregistry.bin").createFile()
    assertThatThrownBy { Key.loadAppKey(file) }.isInstanceOf(RegistryException::class.java)
  }
}
