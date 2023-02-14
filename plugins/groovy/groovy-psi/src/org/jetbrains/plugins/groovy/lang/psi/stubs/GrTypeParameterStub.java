// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;

public class GrTypeParameterStub extends StubBase<GrTypeParameter> implements NamedStub<GrTypeParameter> {
  private final StringRef myName;

  public GrTypeParameterStub(final StubElement parentStub, final StringRef name) {
    super(parentStub, GroovyStubElementTypes.TYPE_PARAMETER);
    myName = name;
  }

  @Override
  public String getName() {
    return StringRef.toString(myName);
  }

}
