// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.NSWorkspace;
import com.intellij.util.system.CpuArch;
import com.sun.jna.*;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.ui.mac.foundation.Foundation.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class FoundationTest {
  @BeforeClass
  public static void assumeMac() {
    IoTestUtil.assumeMacOS();
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

    path = NSWorkspace.absolutePathForAppBundleWithIdentifier("non-existing-blah-blah");
    assertThat(path, nullValue());
  }

  @Test
  public void testPlatformInfo() {
    assertTrue("Word size does not match", Platform.is64Bit());
    assertTrue("Not detected as macOS", Platform.isMac());

    assertEquals("Incorrectly detected as " + CpuArch.CURRENT, CpuArch.CURRENT == CpuArch.X86_64, Platform.isIntel());
    assertEquals("Incorrectly detected as " + CpuArch.CURRENT, CpuArch.CURRENT == CpuArch.ARM64, Platform.isARM());

    assertEquals(1, Native.BOOL_SIZE);
    assertEquals(8, Native.POINTER_SIZE);
    assertEquals(8, Native.SIZE_T_SIZE);
    assertEquals(8, Native.LONG_SIZE);
  }

  @Test
  public void testObjcMsgSend_int() {
    ID number = autorelease(invoke("NSNumber", "numberWithInt:", 123));
    assertEquals(123, invoke(number, "intValue").intValue());
  }

  @Test
  public void testObjcMsgSend_bool() {
    ID number = autorelease(invoke("NSNumber", "numberWithBool:", false));
    assertFalse(invoke(number, "boolValue").booleanValue());
    number = autorelease(invoke("NSNumber", "numberWithBool:", true));
    assertTrue(invoke(number, "boolValue").booleanValue());
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
  public void testObjcMsgSend_vararg_dict() {
    ID number = autorelease(createDict(new String[]{"a", "b", "c"}, new Object[]{"x", "y", "z"}));
    assertEquals("{\n    a = x;\n    b = y;\n    c = z;\n}", toStringViaUTF8(invoke(number, "description")));
  }

  @Test
  public void testObjcMsgSend_vararg_singleString() {
    ID number = autorelease(invokeVarArg("NSArray", "arrayWithObjects:", nsString("Hello")));
    assertEquals("(\n    Hello\n)", toStringViaUTF8(invoke(number, "description")));
  }

  @Test
  public void testObjcMsgSend_vararg_manyStrings() {
    Object[] strings = IntStream.rangeClosed(1, 100).mapToObj(i -> nsString(Integer.toString(i))).toArray();
    ID number = autorelease(invokeVarArg("NSArray", "arrayWithObjects:", strings));
    assertEquals("(" + IntStream.rangeClosed(1, 100).mapToObj(i -> "\n    " + i).collect(Collectors.joining(",")) + "\n)",
                 toStringViaUTF8(invoke(number, "description")));
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
    try (Memory memory = new Memory(16)) {
      int len = cLib.sprintf(memory, "%d plus %d is %d", 3, 5, 8);
      assertEquals(13, len);
      assertEquals("3 plus 5 is 8", memory.getString(0));
    }
  }
}
