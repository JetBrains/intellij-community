// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

public final class GrScriptClassNameIndex extends StringStubIndexExtension<GroovyFile> {
  public static final StubIndexKey<String, GroovyFile> KEY = StubIndexKey.createIndexKey("gr.script.class");

  @Override
  public @NotNull StubIndexKey<String, GroovyFile> getKey() {
    return KEY;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + GrStubUtils.GR_STUB_VERSION;
  }
}
