// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

public final class GrAnnotatedMemberIndex extends StringStubIndexExtension<PsiElement> {
  public static final StubIndexKey<String, PsiElement> KEY = StubIndexKey.createIndexKey("gr.annot.members");

  @Override
  public @NotNull StubIndexKey<String, PsiElement> getKey() {
    return KEY;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + GrStubUtils.GR_STUB_VERSION;
  }
}
