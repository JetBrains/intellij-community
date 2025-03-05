// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.debugger.ScriptPositionManagerHelper;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.runner.GroovyScriptUtil;

public final class GantPositionManagerHelper extends ScriptPositionManagerHelper {

  @Override
  public boolean isAppropriateRuntimeName(final @NotNull String runtimeName) {
    return true;
  }

  @Override
  public boolean isAppropriateScriptFile(final @NotNull GroovyFile scriptFile) {
    return GroovyScriptUtil.isSpecificScriptFile(scriptFile, GantScriptType.INSTANCE);
  }

  @Override
  public PsiFile getExtraScriptIfNotFound(@NotNull ReferenceType refType,
                                          final @NotNull String runtimeName,
                                          final @NotNull Project project,
                                          @NotNull GlobalSearchScope scope) {
    try {
      final String fileName = StringUtil.getShortName(runtimeName);
      PsiFile[] files = FilenameIndex.getFilesByName(project, fileName + "." + GantScriptType.DEFAULT_EXTENSION, scope);
      if (files.length == 0) files = FilenameIndex.getFilesByName(project, fileName + "." + GantScriptType.DEFAULT_EXTENSION, GlobalSearchScope.allScope(project));
      if (files.length == 1) return files[0];

      if (files.length == 0) {
        files = FilenameIndex.getFilesByName(project, fileName + ".groovy", scope);
        if (files.length == 0) files = FilenameIndex.getFilesByName(project, fileName + "." + GantScriptType.DEFAULT_EXTENSION, GlobalSearchScope.allScope(project));

        PsiFile candidate = null;
        for (PsiFile file : files) {
          if (GroovyScriptUtil.isSpecificScriptFile(file, GantScriptType.INSTANCE)) {
            if (candidate != null) return null;
            candidate = file;
          }
        }

        return candidate;
      }
    }
    catch (ProcessCanceledException | IndexNotReadyException ignored) {
    }
    return null;
  }
}
