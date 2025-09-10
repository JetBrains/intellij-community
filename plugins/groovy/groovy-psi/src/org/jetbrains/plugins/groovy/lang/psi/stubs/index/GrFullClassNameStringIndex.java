// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.PsiClass;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

public final class GrFullClassNameStringIndex extends StringStubIndexExtension<PsiClass> {
  public static final StubIndexKey<String, PsiClass> KEY = StubIndexKey.createIndexKey("gr.class.fqn.s");

  @Override
  public @NotNull StubIndexKey<String, PsiClass> getKey() {
    return KEY;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + GrStubUtils.GR_STUB_VERSION;
  }
}
