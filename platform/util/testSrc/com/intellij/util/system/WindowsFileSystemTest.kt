package com.intellij.util.system

import com.sun.jna.Memory
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS.WINDOWS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@EnabledOnOs(WINDOWS)
class WindowsFileSystemTest {
  @Test
  fun `volume flags match JNA for files and directories`(@TempDir directory: Path) {
    val file = Files.createFile(directory.resolve("native-\u6570\u636e.txt"))
    for (path in listOf(directory, file)) {
      assertThat(WindowsFileSystem.isOnDevDrive(path)).isEqualTo(isOnDevDriveViaJna(path))
    }
  }

  private fun isOnDevDriveViaJna(path: Path): Boolean {
    val handle = Kernel32.INSTANCE.CreateFile(
      path.toString(), WinNT.FILE_READ_ATTRIBUTES, WinNT.FILE_SHARE_READ or WinNT.FILE_SHARE_WRITE, null,
      WinNT.OPEN_EXISTING, WinNT.FILE_FLAG_BACKUP_SEMANTICS, null)
    assertThat(handle).isNotEqualTo(WinBase.INVALID_HANDLE_VALUE)
    try {
      Memory(16).use { information ->
        information.clear()
        information.setInt(4, 0x6000)
        information.setInt(8, 1)
        val returned = IntByReference()
        val success = Kernel32.INSTANCE.DeviceIoControl(handle, 0x9023C, information, 16, information, 16, returned, null)
        if (!success) {
          val error = Kernel32.INSTANCE.GetLastError()
          assertThat(Files.getFileStore(path).type()).isEqualTo("NTFS")
          assertThat(error).describedAs("DeviceIoControl(%s)", path).isIn(1, 50, 87)
          return false
        }
        assertThat(returned.value).isEqualTo(16)
        return information.getInt(0) == 0x6000
      }
    }
    finally {
      assertThat(Kernel32.INSTANCE.CloseHandle(handle)).isTrue()
    }
  }
}
