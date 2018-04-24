// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.IndexSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrTypeParameterParameterExtendsListImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;

public final class GrTypeParameterBoundsElementType extends GrReferenceListElementType<GrReferenceList> {

  public GrTypeParameterBoundsElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrReferenceList createPsi(@NotNull GrReferenceListStub stub) {
    return new GrTypeParameterParameterExtendsListImpl(stub, this);
  }

  @Override
  public void indexStub(@NotNull GrReferenceListStub stub, @NotNull IndexSink sink) {
    // don't index short names of bounds as superclasses
  }
}
