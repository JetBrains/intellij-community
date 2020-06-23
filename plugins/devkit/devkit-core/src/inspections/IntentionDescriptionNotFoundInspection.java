// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocatorKt;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class IntentionDescriptionNotFoundInspection extends DescriptionNotFoundInspectionBase {

  public IntentionDescriptionNotFoundInspection() {
    super(DescriptionType.INTENTION);
  }

  @Override
  protected boolean skipIfNotRegistered(PsiClass epClass) {
    final List<ExtensionCandidate> registrations = ExtensionLocatorKt.locateExtensionsByPsiClass(epClass);
    return registrations.isEmpty();
  }

  @Override
  @NotNull
  protected String getHasNotDescriptionError(Module module, PsiClass psiClass) {
    return "Intention does not have a description";
  }

  @Override
  @NotNull
  protected String getHasNotBeforeAfterError() {
    return "Intention must have 'before.*.template' and 'after.*.template' beside 'description.html'";
  }
}
