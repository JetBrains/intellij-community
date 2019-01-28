// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.codeStyleSettings;

import com.intellij.openapi.options.SchemeImportException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

public class CodeStyleXmlImporterTest extends CodeStyleTestCase {
  public void testStandardCodeStyleXml() throws SchemeImportException {
    CodeStyleSettings settings= importSettings();
    assertEquals(false, settings.AUTODETECT_INDENTS);
    assertEquals(60, settings.getDefaultRightMargin());
  }

  public void testProjectCodeStyleSettings() throws SchemeImportException {
    CodeStyleSettings settings= importSettings();
    assertEquals(40, settings.getDefaultRightMargin());
    assertEquals(true, settings.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN);
    assertEquals(true, settings.FORMATTER_TAGS_ENABLED);
    assertEquals(true, settings.FORMATTER_TAGS_ACCEPT_REGEXP);
  }

  public void testNewProjectSettings() throws SchemeImportException {
    CodeStyleSettings settings = importSettings();
    assertThat(settings.getDefaultRightMargin()).isEqualTo(140);
    assertThat(settings.FORMATTER_TAGS_ENABLED).isTrue();
  }

  @Nullable
  @Override
  protected String getTestDir() {
    return "importSettings";
  }
}


