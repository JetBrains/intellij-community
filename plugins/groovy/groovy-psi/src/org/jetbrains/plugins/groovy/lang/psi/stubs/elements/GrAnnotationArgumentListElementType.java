// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.EmptyStub;
import com.intellij.psi.stubs.EmptyStubElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationArgumentListImpl;

public class GrAnnotationArgumentListElementType extends EmptyStubElementType<GrAnnotationArgumentList> {

  public GrAnnotationArgumentListElementType(String debugName) {
    super(debugName, GroovyLanguage.INSTANCE);
  }

  @Override
  public boolean isLeftBound() {
    return true;
  }

  @Override
  public GrAnnotationArgumentList createPsi(@NotNull EmptyStub stub) {
    return new GrAnnotationArgumentListImpl(stub);
  }
}
