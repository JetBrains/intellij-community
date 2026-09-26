// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNameHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

public final class GrChangeSignatureUtil {
  public static @NotNull String getNameWithQuotesIfNeeded(final @NotNull String originalName, final @NotNull Project project) {
    return PsiNameHelper.getInstance(project).isIdentifier(originalName)
           ? originalName
           : GrStringUtil.getLiteralTextByValue(originalName).toString();
  }
}
