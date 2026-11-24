// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.ide.IdeBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
import javax.security.auth.x500.X500Principal

private val JETBRAINS = WindowsPublisher("JetBrains s.r.o.", "CZ")
private val FILE = Path.of("installer.exe")

class MigrationWindowsSignatureTest {
  @Test
  fun `JetBrains certificates of different years give one publisher`() {
    val subjects = listOf(
      // 2025: an EV certificate; the jurisdiction country is not the `C` field
      "CN=JetBrains s.r.o., O=JetBrains s.r.o., L=Prague, C=CZ, SERIALNUMBER=26502275, " +
      "OID.2.5.4.15=Private Organization, OID.1.3.6.1.4.1.311.60.2.1.3=CZ",
      // 2017
      "CN=JetBrains s.r.o., OU=SOFTWARE, O=JetBrains s.r.o., L=Praha, ST=Praha, C=CZ",
      // 2021: an EV certificate of another authority
      "CN=JetBrains s.r.o., O=JetBrains s.r.o., L=Prague, C=CZ, SERIALNUMBER=C 86211, OID.2.5.4.15=Private Organization, " +
      "OID.1.3.6.1.4.1.311.60.2.1.2=Prague, OID.1.3.6.1.4.1.311.60.2.1.3=CZ",
    )
    for (subject in subjects) {
      assertEquals(subject, JETBRAINS, windowsPublisher(X500Principal(subject)))
    }
  }

  @Test
  fun `another organization or another country is another publisher`() {
    assertEquals(WindowsPublisher("Microsoft Corporation", "US"),
                 windowsPublisher(X500Principal("CN=Microsoft Corporation, O=Microsoft Corporation, L=Redmond, ST=Washington, C=US")))
    assertEquals(WindowsPublisher("JetBrains s.r.o.", "US"), windowsPublisher(X500Principal("CN=JetBrains s.r.o., O=JetBrains s.r.o., C=US")))
    // the comparison is exact
    assertNotEquals(JETBRAINS, WindowsPublisher("JetBrains s.r.o", "CZ"))
    assertNotEquals(JETBRAINS, WindowsPublisher("jetbrains s.r.o.", "CZ"))
    assertNotEquals(JETBRAINS, WindowsPublisher("JetBrains s.r.o.", "cz"))
  }

  @Test
  fun `value with a comma is read whole`() {
    assertEquals(WindowsPublisher("GitHub, Inc.", "US"), windowsPublisher(X500Principal("CN=GitHub, O=\"GitHub, Inc.\", C=US")))
  }

  @Test
  fun `name without one organization and one country gives no publisher`() {
    assertNull(windowsPublisher(X500Principal("CN=JetBrains s.r.o., C=CZ")))
    assertNull(windowsPublisher(X500Principal("CN=JetBrains s.r.o., O=JetBrains s.r.o.")))
    assertNull(windowsPublisher(X500Principal("CN=JetBrains s.r.o., O=JetBrains s.r.o., O=Other, C=CZ")))
    assertNull(windowsPublisher(X500Principal("CN=JetBrains s.r.o., O=JetBrains s.r.o., C=CZ, C=US")))
    assertNull(windowsPublisher(X500Principal("CN=JetBrains s.r.o., OU=JetBrains s.r.o., OID.1.3.6.1.4.1.311.60.2.1.3=CZ")))
    assertNull(windowsPublisher(X500Principal("")))
  }

  @Test
  fun `file of the publisher of the running IDE is trusted`() {
    assertTrue(isTrustedWindowsFile(FILE, allowUnsigned = false, runningPublisher = { JETBRAINS }, filePublisher = { JETBRAINS.copy() }))
  }

  @Test
  fun `file of another publisher is not trusted`() {
    val other = WindowsPublisher("Other s.r.o.", "CZ")
    assertFalse(isTrustedWindowsFile(FILE, allowUnsigned = false, runningPublisher = { JETBRAINS }, filePublisher = { other }))
    assertFalse(isTrustedWindowsFile(FILE, allowUnsigned = false, runningPublisher = { other }, filePublisher = { JETBRAINS }))
  }

  @Test
  fun `file with no valid signature is not trusted`() {
    var checked: Path? = null
    assertFalse(isTrustedWindowsFile(FILE, allowUnsigned = false, runningPublisher = { JETBRAINS }, filePublisher = {
      checked = it
      null
    }))
    assertEquals(FILE, checked)
  }

  @Test
  fun `running IDE with no valid signature fails the check`() {
    val error = assertThrows(MigrationInstallException::class.java) {
      isTrustedWindowsFile(FILE, allowUnsigned = false, runningPublisher = { null }, filePublisher = { JETBRAINS })
    }
    assertEquals(IdeBundle.message("updates.migration.error.install"), error.userMessage)
  }

  @Test
  fun `dev property turns the Windows signature check off`() {
    assertTrue(isTrustedWindowsFile(FILE, allowUnsigned = true,
                                    runningPublisher = { error("the check is off") }, filePublisher = { error("the check is off") }))
  }
}
