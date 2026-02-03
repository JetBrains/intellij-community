// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.codeStyleSettings;

import com.intellij.application.options.codeStyle.properties.GeneralCodeStylePropertyMapper;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.impl.source.codeStyle.json.CodeStyleSchemeJsonExporter;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class JsonInterchangeTest extends CodeStyleTestCase {

  public void testExportToJson() throws IOException {
    CodeStyleScheme testScheme = createTestScheme();
    testScheme.getCodeStyleSettings().setDefaultSoftMargins(Arrays.asList(42,62));
    CodeStyleSchemeJsonExporter exporter = new CodeStyleSchemeJsonExporter();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    exporter.exportScheme(testScheme, outputStream, Arrays.asList(GeneralCodeStylePropertyMapper.COMMON_DOMAIN_ID, "html"));
    compareWithExpected(outputStream.toString(), "json");
  }

  @Nullable
  @Override
  protected String getTestDir() {
    return "json";
  }
}
