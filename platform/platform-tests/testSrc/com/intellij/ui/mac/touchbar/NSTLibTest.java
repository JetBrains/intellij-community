// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import org.junit.Test;

import java.lang.foreign.MemorySegment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assume.assumeTrue;

public class NSTLibTest {
  @Test
  public void testLoadingAndBasicFunctions() {
    assumeTrue("NST-unsupported OS", NST.isSupportedOS());

    NSTLibrary lib = null;
    try {
      // NOTE: for supported OS-versions library must always loads (even SystemSettingsWrapper.isTouchBarServerRunning() == false)
      lib = NST.loadLibraryImpl();
    } catch (Throwable e) {
      fail("Failed to load nst library for touchbar: " + e.getMessage());
    }

    assertThat(lib).as("The native Touch Bar library").isNotNull();

    // NOTE: it's difficult to promise correct library work in the system without running tb-server (this condition must be equals to isSettingsDomainExists())
    assumeTrue("touch bar server not running", Helpers.isTouchBarServerRunning());

    try {
      // small check that loaded library can create native objects
      final MemorySegment test = lib.createTouchBar("test", (uid) -> MemorySegment.NULL, null);
      assertThat(test.address()).as("The native Touch Bar object").isNotZero();
      lib.releaseNativePeer(test);
    }
    catch (RuntimeException e) {
      fail("nst library was loaded, but native object can't be created: " + e.getMessage());
    }

    // TODO:
    // 1. try to create emulation of OS-event when click touchbar (need to make some research with dtrace)
    // 2. check that created objects are valid
  }
}
