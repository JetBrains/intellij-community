// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.NSWorkspace;
import com.intellij.util.io.jna.DisposableMemory;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.intellij.ui.mac.foundation.Foundation.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class FoundationTest {
  @BeforeClass
  public static void assumeMac() {
    Assume.assumeTrue("mac only", SystemInfo.isMac);
  }

  @Test
  public void testStrings() {
    assertThat(toStringViaUTF8(nsString("Test")), equalTo("Test"));
  }

  @Test
  public void testEncodings() {
    assertThat(getEncodingName(4), equalTo("utf-8"));
    assertThat(getEncodingName(0), nullValue());
    assertThat(getEncodingName(-1), nullValue());

    assertThat(getEncodingCode("utf-8"), equalTo(4L));
    assertThat(getEncodingCode("UTF-8"), equalTo(4L));

    assertThat(getEncodingCode(""), equalTo(-1L));
    //noinspection SpellCheckingInspection
    assertThat(getEncodingCode("asdasd"), equalTo(-1L));
    assertThat(getEncodingCode(null), equalTo(-1L));

    assertThat(getEncodingName(10), equalTo("utf-16"));
    assertThat(getEncodingCode("utf-16"), equalTo(10L));

    assertThat(getEncodingName(2483028224L), equalTo("utf-16le"));
    assertThat(getEncodingCode("utf-16le"), equalTo(2483028224L));
    assertThat(getEncodingName(2415919360L), equalTo("utf-16be"));
    assertThat(getEncodingCode("utf-16be"), equalTo(2415919360L));
  }

  @Test
  public void testFindingAppBundle() {
    String path;

    path = NSWorkspace.absolutePathForAppBundleWithIdentifier("com.apple.Finder");
    assertThat(path, notNullValue());
    assertThat(path, endsWith("Finder.app"));

    path = NSWorkspace.absolutePathForAppBundleWithIdentifier("unexisting-bla-blah");
    assertThat(path, nullValue());
  }

  @Test
  public void testPlatformInfo() {
    assertEquals("bitness does not match", SystemInfo.is64Bit, Platform.is64Bit());
    assertTrue("not detected as mac", Platform.isMac());

    boolean isIntel = SystemInfo.is32Bit || SystemInfo.isMacIntel64;
    assertEquals((isIntel ? "not " : "") + "detected as Intel", isIntel, Platform.isIntel());
    assertEquals((!isIntel ? "not " : "") + "detected as arm", !isIntel, Platform.isARM());

    assertEquals(1, Native.BOOL_SIZE);
    assertEquals(SystemInfo.is32Bit ? 4 : 8, Native.POINTER_SIZE);
    assertEquals(SystemInfo.is32Bit ? 4 : 8, Native.SIZE_T_SIZE);
    assertEquals(SystemInfo.is32Bit ? 4 : 8, Native.LONG_SIZE);
  }

  @Test
  public void testObjcMsgSend_int() {
    ID number = autorelease(invoke("NSNumber", "numberWithInt:", 123));
    assertEquals(123, invoke(number, "intValue").intValue());
  }

  @Test
  public void testObjcMsgSend_bool() {
    ID number = autorelease(invoke("NSNumber", "numberWithBool:", false));
    assertFalse(invoke(number, "boolValue").intValue() != 0);
    number = autorelease(invoke("NSNumber", "numberWithBool:", true));
    assertTrue(invoke(number, "boolValue").intValue() != 0);
  }

  @Test
  public void testObjcMsgSend_char() {
    ID number = autorelease(invoke("NSNumber", "numberWithChar:", 'y'));
    assertEquals((int)'y', invoke(number, "charValue").intValue());
  }

  @Test
  public void testObjcMsgSend_long() {
    ID number = autorelease(invoke("NSNumber", "numberWithLong:", 0x4fffffffffffffffL));
    assertEquals(0x4fffffffffffffffL, invoke(number, "longValue").longValue());
  }

  @Test
  public void testObjcMsgSend_string() {
    ID number = autorelease(invoke("NSNumber", "numberWithLong:", 0x4fffffffffffffffL));
    assertEquals("5764607523034234879", toStringViaUTF8(invoke(number, "description")));
  }

  @Test
  public void testObjcMsgSend_double() {
    ID number = autorelease(invoke("NSNumber", "numberWithDouble:", Math.PI));
    assertEquals("3.141592653589793", toStringViaUTF8(invoke(number, "description")));
    assertEquals(Math.PI, invoke_fpret(number, "doubleValue"), 0.0);
  }

  @Test
  public void testObjcMsgSend_vararg() {
    ID number = autorelease(invokeVarArg("NSArray", "arrayWithObjects:",
                                         autorelease(invoke("NSNumber", "numberWithInt:", 1)),
                                         autorelease(invoke("NSNumber", "numberWithInt:", 2)),
                                         autorelease(invoke("NSNumber", "numberWithInt:", 3)),
                                         autorelease(invoke("NSNumber", "numberWithInt:", 4)),
                                         autorelease(invoke("NSNumber", "numberWithInt:", 5))));
    assertEquals("(\n    1,\n    2,\n    3,\n    4,\n    5\n)", toStringViaUTF8(invoke(number, "description")));
  }

  @Test
  public void testSelector() {
    Pointer sel = createSelector("respondsToSelector:");
    assertEquals("respondsToSelector:", stringFromSelector(sel));
  }

  @Test
  public void testClassName() {
    ID clazz = getObjcClass("NSObject");
    assertEquals("NSObject", stringFromClass(clazz));
  }

  interface CLib extends Library {
    int sprintf(Pointer target, String format, Object... args);
  }

  @Test
  public void testSprintf() {
    CLib cLib = Native.load("c", CLib.class);
    DisposableMemory memory = new DisposableMemory(16);
    try {
      int len = cLib.sprintf(memory, "%d plus %d is %d", 3, 5, 8);
      assertEquals(13, len);
      assertEquals("3 plus 5 is 8", memory.getString(0));
    }
    finally {
      memory.dispose();
    }
  }
}
