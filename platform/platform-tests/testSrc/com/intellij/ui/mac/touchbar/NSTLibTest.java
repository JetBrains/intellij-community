// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.sun.jna.Pointer;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
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

    assertNotNull("Failed to load nst library for touchbar: native loader returns null", lib);

    // NOTE: it's difficult to promise correct library work in the system without running tb-server (this condition must be equals to isSettingsDomainExists())
    assumeTrue("touch bar server not running", Helpers.isTouchBarServerRunning());

    try {
      // small check that loaded library can create native objects
      final Pointer test = lib.createTouchBar("test", (uid) -> null, null);
      assertNotNull("Failed to create native touchbar object, result is null", test);
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
