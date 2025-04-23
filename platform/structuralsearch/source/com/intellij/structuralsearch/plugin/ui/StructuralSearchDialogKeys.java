// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.util.Key;

public final class StructuralSearchDialogKeys {
  public static final Key<StructuralSearchDialog> STRUCTURAL_SEARCH_DIALOG = Key.create("STRUCTURAL_SEARCH_DIALOG");
  public static final Key<String> STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID = Key.create("STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID");
  public static final Key<Runnable> STRUCTURAL_SEARCH_ERROR_CALLBACK = Key.create("STRUCTURAL_SEARCH_ERROR_CALLBACK");
  public static final Key<Boolean> TEST_STRUCTURAL_SEARCH_DIALOG = Key.create("TEST_STRUCTURAL_SEARCH_DIALOG");

  /**
   * @deprecated AI code generation for the structural search is not supported anymore.
   */
  @Deprecated
  public static final Key<Boolean> AI_SUPPORT_KEY = Key.create("ssr.ai.support");
}
