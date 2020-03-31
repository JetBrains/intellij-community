// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.NSWorkspace;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class FoundationTest {
  @BeforeClass
  public static void assumeMac() {
    Assume.assumeTrue("mac only", SystemInfo.isMac);
  }

  @Test
  public void testStrings() {
    assertThat(Foundation.toStringViaUTF8(Foundation.nsString("Test")), equalTo("Test"));
  }

  @Test
  public void testEncodings() {
    assertThat(Foundation.getEncodingName(4), equalTo("utf-8"));
    assertThat(Foundation.getEncodingName(0), nullValue());
    assertThat(Foundation.getEncodingName(-1), nullValue());

    assertThat(Foundation.getEncodingCode("utf-8"), equalTo(4L));
    assertThat(Foundation.getEncodingCode("UTF-8"), equalTo(4L));

    assertThat(Foundation.getEncodingCode(""), equalTo(-1L));
    //noinspection SpellCheckingInspection
    assertThat(Foundation.getEncodingCode("asdasd"), equalTo(-1L));
    assertThat(Foundation.getEncodingCode(null), equalTo(-1L));

    assertThat(Foundation.getEncodingName(10), equalTo("utf-16"));
    assertThat(Foundation.getEncodingCode("utf-16"), equalTo(10L));

    assertThat(Foundation.getEncodingName(2483028224L), equalTo("utf-16le"));
    assertThat(Foundation.getEncodingCode("utf-16le"), equalTo(2483028224L));
    assertThat(Foundation.getEncodingName(2415919360L), equalTo("utf-16be"));
    assertThat(Foundation.getEncodingCode("utf-16be"), equalTo(2415919360L));
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
}
