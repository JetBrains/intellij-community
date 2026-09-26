// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

import java.io.File;
import java.io.IOException;

public abstract class StructuralReplaceTestCase extends LightPlatformCodeInsightTestCase {
  protected ReplaceOptions options;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    options = new ReplaceOptions();
  }

  protected String loadFile(String fileName) throws IOException {
    return FileUtilRt.loadFile(new File(getTestDataPath() + FileUtilRt.getExtension(fileName) + "/" + fileName), CharsetToolkit.UTF8, true);
  }

  protected String replace(String in, String what, String by) {
    return replace(in, what, by, false);
  }

  protected String replace(String in, String what, String by, boolean sourceIsFile) {
    return replace(in, what, by, sourceIsFile, false);
  }

  protected String replace(String in, String what, String by, boolean sourceIsFile, boolean createPhysicalFile) {
    if (in == null && (sourceIsFile || createPhysicalFile)) {
      throw new IllegalArgumentException("can't create file when 'in' argument is null");
    }
    final MatchOptions matchOptions = options.getMatchOptions();
    if (createPhysicalFile) {
      configureFromFileText("Source." + matchOptions.getFileType().getDefaultExtension(), in);
      matchOptions.setScope(new LocalSearchScope(getFile()));
    }
    matchOptions.fillSearchCriteria(what);
    final CompiledPattern compiledPattern = PatternCompiler.compilePattern(getProject(), matchOptions, true, false);
    final String message = StructuralSearchTestCase.checkApplicableConstraints(matchOptions, compiledPattern);
    assertNull(message, message);
    return Replacer.testReplace(in, what, by, options, getProject(), sourceIsFile);
  }
}
