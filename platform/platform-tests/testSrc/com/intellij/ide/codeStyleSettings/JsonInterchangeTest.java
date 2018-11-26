// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.codeStyleSettings;

import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.impl.source.codeStyle.json.CodeStyleSchemeJsonExporter;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class JsonInterchangeTest extends CodeStyleTestCase {

  public void testExportToJson() throws IOException {
    CodeStyleScheme testScheme = createTestScheme();
    CodeStyleSchemeJsonExporter exporter = new CodeStyleSchemeJsonExporter().filter("All");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    exporter.exportScheme(testScheme, outputStream);
    String expected = loadExpected("json");
    assertEquals(expected, outputStream.toString());
  }

  @Nullable
  @Override
  protected String getTestDir() {
    return "json";
  }
}
