// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.actions;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.JavaCreateFromTemplateHandler;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

final class JavaFxCreateFromTemplateHandler extends JavaCreateFromTemplateHandler {
  @Override
  public boolean handlesTemplate(@NotNull FileTemplate template) {
    return "JavaFXApplication".equals(template.getName());
  }

  @Override
  public boolean canCreate(PsiDirectory @NotNull [] dirs) {
    // see CreateJavaFxApplicationAction
    return false;
  }
}
