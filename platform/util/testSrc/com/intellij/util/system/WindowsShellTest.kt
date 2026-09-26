package com.intellij.util.system

import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.WINDOWS
import java.util.UUID

@EnabledOnOs(WINDOWS)
class WindowsShellTest {
  @Test
  fun `known folder paths match JNA`() {
    for ((folder, reference) in listOf(
      WindowsShell.FOLDERID_DESKTOP to KnownFolders.FOLDERID_Desktop,
      WindowsShell.FOLDERID_DOWNLOADS to KnownFolders.FOLDERID_Downloads,
      WindowsShell.FOLDERID_LOCAL_APP_DATA to KnownFolders.FOLDERID_LocalAppData,
    )) {
      assertThat(WindowsShell.knownFolderPath(folder)).isEqualTo(Shell32Util.getKnownFolderPath(reference))
    }
  }

  @Test
  fun `unknown folder has no path`() {
    assertThat(WindowsShell.knownFolderPath(UUID(0, 0))).isNull()
  }
}
