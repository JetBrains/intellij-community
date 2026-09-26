// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

/**
 * @author Bas Leijdekkers
 */
public abstract class DetectFileTypeTestCase extends LightPlatformCodeInsightTestCase {

  protected void doTest(LanguageFileType fileType, String text) {
    doTest(fileType, text, null);
  }

  protected void doTest(LanguageFileType fileType, String text, String ext) {
    if (ext == null) ext = fileType.getDefaultExtension();
    configureFromFileText("test." + ext, text, true);
    final SearchContext context = new SearchContext(getProject(), getFile(), getEditor());
    assertEquals(fileType, UIUtil.detectFileType(context));
  }
}
