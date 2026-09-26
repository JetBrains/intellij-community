// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.jsonpath.JsonPathFileType;
import com.intellij.jsonpath.JsonPathLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public final class JsonPathFile extends PsiFileBase {
  public JsonPathFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, JsonPathLanguage.INSTANCE);
  }

  @Override
  public @NotNull FileType getFileType() {
    return JsonPathFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "JsonPathFile";
  }
}
