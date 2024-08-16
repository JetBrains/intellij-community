// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CompletionAutoPopupTester extends CompletionAutoPopupTesterBase {
  private final CodeInsightTestFixture myFixture;

  public CompletionAutoPopupTester(CodeInsightTestFixture fixture) {
    myFixture = fixture;
  }

  @Override
  public LookupImpl getLookup() {
    return (LookupImpl)myFixture.getLookup();
  }

  @Override
  protected @Nullable Editor getEditor() {
    return myFixture.getEditor();
  }

  @Override
  protected @NotNull Project getProject() {
    return myFixture.getProject();
  }

  @Override
  protected void type(char c) {
      myFixture.type(c);
  }
}
