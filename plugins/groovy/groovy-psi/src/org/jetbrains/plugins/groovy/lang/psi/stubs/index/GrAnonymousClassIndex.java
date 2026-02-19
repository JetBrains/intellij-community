// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

/**
 * @author Maxim.Medvedev
 */
public final class GrAnonymousClassIndex extends StringStubIndexExtension<GrAnonymousClassDefinition> {
  public static final StubIndexKey<String, GrAnonymousClassDefinition> KEY = StubIndexKey.createIndexKey("gr.anonymous.class");

  @Override
  public @NotNull StubIndexKey<String, GrAnonymousClassDefinition> getKey() {
    return KEY;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + GrStubUtils.GR_STUB_VERSION + 1;
  }
}
