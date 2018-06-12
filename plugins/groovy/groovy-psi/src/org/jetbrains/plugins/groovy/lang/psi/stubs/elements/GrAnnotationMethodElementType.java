// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.IndexSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrAnnotationMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotationMethodNameIndex;

public class GrAnnotationMethodElementType extends GrMethodElementType {

  public GrAnnotationMethodElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrMethod createPsi(@NotNull GrMethodStub stub) {
    return new GrAnnotationMethodImpl(stub);
  }

  @Override
  public void indexStub(@NotNull GrMethodStub stub, @NotNull IndexSink sink) {
    super.indexStub(stub, sink);
    String name = stub.getName();
    sink.occurrence(GrAnnotationMethodNameIndex.KEY, name);
  }
}
