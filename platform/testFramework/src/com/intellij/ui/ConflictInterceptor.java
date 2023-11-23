// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.refactoring.ui.ConflictsDialog;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * An interceptor that expects a conflict dialog. An interceptor assumes that "Continue" button will be pressed
 */
public class ConflictInterceptor extends UiInterceptors.UiInterceptor<ConflictsDialog> {
  private final Set<String> myConflicts;

  /**
   * @param expectedConflicts list of expected conflicts
   */
  public ConflictInterceptor(Set<String> expectedConflicts) {
    super(ConflictsDialog.class);
    myConflicts = expectedConflicts;
  }
  
  @Override
  protected void doIntercept(@NotNull ConflictsDialog component) {
    assertEquals(myConflicts, component.getConflictDescriptions());
  }
}
