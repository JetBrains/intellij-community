// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import com.intellij.openapi.util.io.FileAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeAccessTest {
  /// `ServiceLoader` must find the util-ex provider on the same class path as `util`.
  @Test void providerIsRegistered() {
    assertThat(NativeAccess.getInstance()).isInstanceOf(NativeAccessImpl.class);
  }

  /// `kill(pid, 0)` checks that the process exists and delivers nothing.
  @Test void signalZeroReachesThisProcess() {
    assumeTrue(OS.CURRENT != OS.Windows);

    long pid = ProcessHandle.current().pid();
    assertThat(NativeAccess.getInstance().kill((int)pid, 0)).isEqualTo(0);
    assertThat(PosixSignals.kill((int)pid, 0)).isEqualTo(0);
  }

  @Test void killIsUnavailableOnWindows() {
    assumeTrue(OS.CURRENT == OS.Windows);

    assertThatThrownBy(() -> NativeAccess.getInstance().kill(1, 0)).isInstanceOf(IllegalStateException.class);
  }

  /// The temp directory is on a real volume, so the file system probe must answer.
  @Test void temporaryDirectoryHasKnownCaseSensitivity(@TempDir Path tempDir) {
    assumeTrue(OS.CURRENT == OS.Windows || OS.CURRENT == OS.macOS);

    assertThat(NativeAccess.getInstance().getDirectoryCaseSensitivity(tempDir)).isNotEqualTo(FileAttributes.CaseSensitivity.UNKNOWN);
  }

  /// A missing directory is "unknown", not an error, because the caller falls back to Java I/O.
  @Test void missingDirectoryIsUnknown(@TempDir Path tempDir) {
    Path missing = tempDir.resolve("missing");
    assertThat(NativeAccess.getInstance().getDirectoryCaseSensitivity(missing)).isEqualTo(FileAttributes.CaseSensitivity.UNKNOWN);
  }

  @Test void plainDirectoryIsNoReparsePoint(@TempDir Path tempDir) {
    assumeTrue(OS.CURRENT == OS.Windows);

    assertThat(NativeAccess.getInstance().isReparsePoint(tempDir)).isFalse();
    assertThat(WindowsFileSystem.isReparsePoint(tempDir)).isFalse();
  }

  @Test void reparsePointIsUnknownOffWindows(@TempDir Path tempDir) {
    assumeTrue(OS.CURRENT != OS.Windows);

    assertThat(NativeAccess.getInstance().isReparsePoint(tempDir)).isNull();
  }

  /// Exercises the real `sysctlbyname` and `pathconf` downcalls.
  @Test void macProbesAnswer() {
    assumeTrue(OS.CURRENT == OS.macOS);

    assertThat(NativeAccess.getInstance().isTranslatedProcess()).isNotNull();
    assertThat(Sysctl.intByName("hw.ncpu")).isPositive();
    assertThat(Sysctl.intByName("hw.no_such_value")).isNull();
    assertThat(MacFileSystem.caseSensitivity("/")).isNotEqualTo(FileAttributes.CaseSensitivity.UNKNOWN);
    assertThat(MacFileSystem.caseSensitivity("/no/such/directory")).isEqualTo(FileAttributes.CaseSensitivity.UNKNOWN);
  }

  /// Exercises the real `RegGetValueW` and `IsWow64Process2` downcalls.
  @Test void windowsProbesAnswer() {
    assumeTrue(OS.CURRENT == OS.Windows);

    assertThat(NativeAccess.getInstance().getWindowsBuildNumber()).isPositive();
    assertThat(NativeAccess.getInstance().getWindowsNativeArch()).isNotNull();
  }

  /// `statfs` answers for the root; only a few file systems map to a known result.
  @Test void linuxProbeDoesNotFail() {
    assumeTrue(OS.CURRENT == OS.Linux);

    assertThat(LinuxFileSystem.caseSensitivity("/")).isNotNull();
    assertThat(LinuxFileSystem.caseSensitivity("/no/such/directory")).isEqualTo(FileAttributes.CaseSensitivity.UNKNOWN);
  }

  /// The probes stay silent off their platform, because the callers treat `null` as "unknown".
  @Test void foreignProbesStayNull() {
    NativeAccess access = NativeAccess.getInstance();
    if (OS.CURRENT != OS.Windows) {
      assertThat(access.getWindowsBuildNumber()).isNull();
      assertThat(access.getWindowsNativeArch()).isNull();
    }
    if (OS.CURRENT != OS.macOS) {
      assertThat(access.isTranslatedProcess()).isNull();
    }
  }
}
