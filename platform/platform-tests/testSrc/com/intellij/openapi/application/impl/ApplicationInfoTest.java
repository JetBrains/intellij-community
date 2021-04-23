// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.util.JDOMUtil;
import org.intellij.lang.annotations.Language;
import org.jdom.JDOMException;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class ApplicationInfoTest {
  @Test
  public void shortenCompanyName() throws Exception {
    assertShortCompanyName("<component><company name='Google Inc.'/></component>", "Google");
    assertShortCompanyName("<component><company name='JetBrains s.r.o.'/></component>", "JetBrains");
    assertShortCompanyName("<component><company shortName='Acme Inc.'/></component>", "Acme Inc.");
  }

  private static void assertShortCompanyName(@Language("XML") String xml, String expected) throws IOException, JDOMException {
    assertEquals(expected, new ApplicationInfoImpl(JDOMUtil.load(xml)).getShortCompanyName());
  }
}
