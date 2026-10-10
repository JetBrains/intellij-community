// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan;

import com.intellij.model.SymbolPointer;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * What a caller asks a {@link ModPlanRefactoring} to do: the project, the symbols the refactoring is about and the options.
 * The symbols are held by pointers, so a request outlives a read action and a dialog.
 *
 * @param subjects     the symbols the refactoring is about; at least one. A question does not change them.
 * @param optionValues the option values of the request. They change after each question: the request of the next step also holds the
 *                     answers, see {@link ModPlanChain#withOptionValues}.
 */
public record ModPlanRequest(@NotNull Project project,
                             @NotNull @Unmodifiable List<SymbolPointer> subjects,
                             @NotNull ModPlanOptionValues optionValues) {
  /**
   * @throws IllegalArgumentException if there is no subject
   */
  public ModPlanRequest {
    if (subjects.isEmpty()) {
      throw new IllegalArgumentException("No subject");
    }
    subjects = List.copyOf(subjects);
  }
}
