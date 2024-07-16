// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jsonpath.psi;

import com.intellij.jsonpath.JsonPathFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

final class JsonPathElementGenerator {
  private final Project myProject;

  JsonPathElementGenerator(@NotNull Project project) {
    myProject = project;
  }

  public @NotNull PsiFile createDummyFile(@NotNull String content) {
    var psiFileFactory = PsiFileFactory.getInstance(myProject);
    return psiFileFactory.createFileFromText("dummy." + JsonPathFileType.INSTANCE.getDefaultExtension(),
                                             JsonPathFileType.INSTANCE,
                                             content);
  }

  public @NotNull PsiElement createStringLiteral(String unescapedContent) {
    PsiFile file = createDummyFile("['" + StringUtil.escapeStringCharacters(unescapedContent) + "']");
    JsonPathQuotedPathsList quotedPathsList = ((JsonPathExpressionSegment)file.getFirstChild()).getQuotedPathsList();
    return requireNonNull(quotedPathsList).getStringLiteralList().get(0);
  }
}
