// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.FakePsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GdslNamedParameter extends FakePsiElement {

  private final String myName;
  public final @NlsSafe String docString;
  private final PsiElement myParent;
  @NlsSafe public final @Nullable String myParameterTypeText;

  public GdslNamedParameter(String name, String doc, @NotNull PsiElement parent, @Nullable String type) {
    myName = name;
    this.docString = doc;
    myParent = parent;
    myParameterTypeText = type;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public String getName() {
    return myName;
  }
}
