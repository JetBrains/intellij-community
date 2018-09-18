// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.quickfix.CreateHtmlDescriptionFix;

/**
 * @author Konstantin Bulenkov
 */
public class IntentionDescriptionNotFoundInspection extends DescriptionNotFoundInspectionBase {

  public IntentionDescriptionNotFoundInspection() {
    super(DescriptionType.INTENTION);
  }

  @Override
  protected CreateHtmlDescriptionFix getFix(Module module, String descriptionDir) {
    return new CreateHtmlDescriptionFix(descriptionDir, module, DescriptionType.INTENTION);
  }

  @Override
  @NotNull
  protected String getHasNotDescriptionError() {
    return "Intention does not have a description";
  }

  @Override
  @NotNull
  protected String getHasNotBeforeAfterError() {
    return "Intention must have 'before.*.template' and 'after.*.template' beside 'description.html'";
  }

  @Override
  @NotNull
  public String getShortName() {
    return "IntentionDescriptionNotFoundInspection";
  }
}
