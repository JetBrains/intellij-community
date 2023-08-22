// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNameHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

public final class GrChangeSignatureUtil {
  @NotNull
  public static String getNameWithQuotesIfNeeded(@NotNull final String originalName, @NotNull final Project project) {
    return PsiNameHelper.getInstance(project).isIdentifier(originalName)
           ? originalName
           : GrStringUtil.getLiteralTextByValue(originalName).toString();
  }
}
