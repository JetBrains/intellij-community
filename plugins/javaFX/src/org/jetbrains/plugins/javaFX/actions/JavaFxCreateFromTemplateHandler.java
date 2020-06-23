// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.actions;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.JavaCreateFromTemplateHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

public class JavaFxCreateFromTemplateHandler extends JavaCreateFromTemplateHandler {
  @Override
  public boolean handlesTemplate(@NotNull FileTemplate template) {
    return "JavaFXApplication".equals(template.getName());
  }

  @Override
  public boolean canCreate(PsiDirectory @NotNull [] dirs) {
    if (dirs.length > 0) {
      Project project = dirs[0].getProject();
      if (JavaPsiFacade.getInstance(project).findPackage("javafx") == null) {
        return false;
      }
    }
    return super.canCreate(dirs);
  }
}
