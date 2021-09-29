// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeStyle;

import com.intellij.application.options.codeStyle.excludedFiles.GlobPatternDescriptor;
import com.intellij.application.options.codeStyle.excludedFiles.NamedScopeDescriptor;
import com.intellij.application.options.codeStyle.excludedFiles.NamedScopeToGlobConverter;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

public class ScopeToGlobConversionTest extends LightPlatformTestCase {

  public void testConvertSinglePattern() throws ParsingException {
    NamedScopeDescriptor descriptor = new NamedScopeDescriptor(createScope("test", "file:*.min.js"));
    GlobPatternDescriptor globDescriptor = NamedScopeToGlobConverter.convert(descriptor);
    assertEquals("*.min.js", globDescriptor.getPattern());
  }

  public void testConvertMultiplePatterns() throws ParsingException {
    NamedScopeDescriptor descriptor = new NamedScopeDescriptor(createScope("test", "file:*.js||file:*.java"));
    GlobPatternDescriptor globDescriptor = NamedScopeToGlobConverter.convert(descriptor);
    assertEquals("{*.js,*.java}", globDescriptor.getPattern());
  }

  public void testNoConversion() throws ParsingException {
    NamedScopeDescriptor descriptor = new NamedScopeDescriptor(createScope("test", "file:*.js||file://*.java"));
    GlobPatternDescriptor globDescriptor = NamedScopeToGlobConverter.convert(descriptor);
    assertNull(globDescriptor);
  }

  private NamedScope createScope(@NotNull String name, @NotNull String pattern) throws ParsingException {
    NamedScopesHolder holder = NamedScopeManager.getInstance(getProject());
    PackageSet fileSet = PackageSetFactory.getInstance().compile(pattern);
    NamedScope scope = holder.createScope(name, fileSet);
    holder.addScope(scope);
    return scope;
  }
}
