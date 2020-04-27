// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ApplicationInfoTest {
  @Test
  public void shortenCompanyName() {
    assertEquals("Google", ApplicationInfoImpl.shortenCompanyName("Google Inc."));
    assertEquals("JetBrains", ApplicationInfoImpl.shortenCompanyName("JetBrains s.r.o."));
  }
}
