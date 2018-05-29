// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.ID;
import junit.framework.TestCase;
import org.junit.Assume;
import org.junit.Test;

public class NSTLibTest extends TestCase {
  @Test
  public void testLoadingAndBasicFunctions() {
    Assume.assumeTrue(NST.isSupportedOS());

    NSTLibrary lib = null;
    try {
      // NOTE: for supported OS-versions library must always loads (even SystemSettingsWrapper.isTouchBarServerRunning() == false)
      lib = NST.loadLibrary();
    } catch (Throwable e) {
      fail("Failed to load nst library for touchbar: " + e.getMessage());
    }

    assertNotNull("Failed to load nst library for touchbar: native loader returns null", lib);

    // NOTE: it's difficult to promise correct library work in the system without running tb-server (this condition must be equals to isSettingsDomainExists())
    Assume.assumeTrue(SystemSettingsWrapper.isTouchBarServerRunning());

    try {
      // small check that loaded library can create native objects
      final ID test = lib.createTouchBar("test", (uid) -> { return ID.NIL;}, null);
      assertNotNull("Failed to create native touchbar object, result is null", test);
      assertFalse("Failed to create native touchbar object, result is ID.NIL", test == ID.NIL);
      if (test != ID.NIL)
        lib.releaseTouchBar(test);
    } catch (RuntimeException e) {
      fail("nst library was loaded, but native object can't be created: " + e.getMessage());
    }

    // TODO:
    // 1. try to create emulation of OS-event when click touchbar (need to make some research with dtrace)
    // 2. check that created objects are valid
  }
}
