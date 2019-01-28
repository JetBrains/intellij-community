// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

public class GrVariableDeclarationStub extends StubBase<GrVariableDeclaration> {

  private final @Nullable String myTypeString;

  private volatile SoftReference<GrTypeElement> myTypeElement;

  public GrVariableDeclarationStub(StubElement parent, @Nullable String typeString) {
    super(parent, GroovyStubElementTypes.VARIABLE_DECLARATION);
    myTypeString = typeString;
  }

  @Nullable
  public String getTypeString() {
    return myTypeString;
  }

  @Nullable
  public GrTypeElement getTypeElement() {
    String typeString = getTypeString();
    if (typeString == null) return null;

    GrTypeElement typeElement = SoftReference.dereference(myTypeElement);
    if (typeElement == null) {
      typeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(typeString, getPsi());
      myTypeElement = new SoftReference<>(typeElement);
    }

    return typeElement;
  }
}
