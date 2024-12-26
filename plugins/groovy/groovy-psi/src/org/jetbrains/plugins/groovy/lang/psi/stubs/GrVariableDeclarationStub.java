// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

public class GrVariableDeclarationStub extends StubBase<GrVariableDeclaration> {

  private final @Nullable String myTypeString;

  private volatile SoftReference<GrTypeElement> myTypeElement;

  public GrVariableDeclarationStub(StubElement parent, @Nullable String typeString) {
    super(parent, GroovyStubElementTypes.VARIABLE_DECLARATION);
    myTypeString = typeString;
  }

  public @Nullable String getTypeString() {
    return myTypeString;
  }

  public @Nullable GrTypeElement getTypeElement() {
    String typeString = getTypeString();
    if (typeString == null) return null;

    GrTypeElement typeElement = dereference(myTypeElement);
    if (typeElement == null) {
      typeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(typeString, getPsi());
      myTypeElement = new SoftReference<>(typeElement);
    }

    return typeElement;
  }
}
