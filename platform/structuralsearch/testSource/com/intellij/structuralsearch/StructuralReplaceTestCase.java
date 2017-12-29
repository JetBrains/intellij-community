// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;

import java.io.File;
import java.io.IOException;

abstract class StructuralReplaceTestCase extends LightQuickFixTestCase {
  protected Replacer replacer;
  protected ReplaceOptions options;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_4);

    options = new ReplaceOptions();
    replacer = new Replacer(getProject(), null);
  }

  protected String loadFile(String fileName) throws IOException {
    return FileUtilRt.loadFile(new File(getTestDataPath() + FileUtilRt.getExtension(fileName) + "/" + fileName), CharsetToolkit.UTF8, true);
  }
}
