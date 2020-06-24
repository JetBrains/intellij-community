// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

public abstract class GrStubElementType<S extends StubElement<?>, T extends GroovyPsiElement> extends IStubElementType<S, T> {
  public GrStubElementType(@NonNls @NotNull String debugName) {
    super(debugName, GroovyLanguage.INSTANCE);
  }

  @Override
  public void indexStub(@NotNull S stub, @NotNull IndexSink sink) {
  }

  @Override
  public @NotNull String getExternalId() {
    return "gr." + super.toString();
  }
}
