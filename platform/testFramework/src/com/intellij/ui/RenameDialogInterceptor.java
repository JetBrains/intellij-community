// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.refactoring.rename.RenameRefactoringDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.fail;

/**
 * Interceptor for the next rename refactoring dialog
 */
public class RenameDialogInterceptor extends UiInterceptors.UiInterceptor<RenameRefactoringDialog> {
  private final @NotNull String myNewName;
  private final @Nullable List<String> mySuggested;

  /**
   * Create an interceptor which will intercept the RenameRefactoringDialog and force to rename to a specified name
   *
   * @param newName the name to rename to
   */
  public RenameDialogInterceptor(@NotNull String newName) {
    super(RenameRefactoringDialog.class);
    myNewName = newName;
    mySuggested = null;
  }

  /**
   * Create an interceptor which will intercept the RenameRefactoringDialog, check the suggested names list
   * and force to rename to a specified name
   *
   * @param newName the name to rename to
   * @param expectedSuggestions list of expected suggested names. The interceptor will fail if actual names mismatch.
   *                            The order is important.
   */
  public RenameDialogInterceptor(@NotNull String newName, @NotNull List<@NotNull String> expectedSuggestions) {
    super(RenameRefactoringDialog.class);
    myNewName = newName;
    mySuggested = expectedSuggestions;
  }

  @Override
  protected void doIntercept(@NotNull RenameRefactoringDialog component) {
    if (mySuggested != null) {
      String[] names = component.getSuggestedNames();
      List<String> actual = Arrays.asList(names);
      if (!mySuggested.equals(actual)) {
        fail("Expected suggested names: " + mySuggested + ", actual: " + actual);
      }
    }
    try {
      component.performRename(myNewName);
    }
    finally {
      component.close(); // to avoid dialog leak
    }
  }

  @Override
  public String toString() {
    return "RenameDialogInterceptor that renames an element to '" + myNewName + "'";
  }
}
