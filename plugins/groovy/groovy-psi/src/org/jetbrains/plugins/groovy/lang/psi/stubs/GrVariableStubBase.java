// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

public abstract class GrVariableStubBase<V extends GrVariable> extends StubBase<V> implements NamedStub<V> {

  private final @Nullable StringRef myNameRef;
  private final String @NotNull [] myAnnotations;
  private final @Nullable String myTypeText;

  private SoftReference<GrTypeElement> myTypeElement;

  protected GrVariableStubBase(StubElement parent,
                               IStubElementType elementType,
                               @Nullable StringRef ref,
                               String @NotNull [] annotations,
                               @Nullable String text) {
    super(parent, elementType);
    myNameRef = ref;
    myAnnotations = annotations;
    myTypeText = text;
  }

  @Override
  public @Nullable String getName() {
    return StringRef.toString(myNameRef);
  }

  public String @NotNull [] getAnnotations() {
    return myAnnotations;
  }

  public @Nullable String getTypeText() {
    return myTypeText;
  }

  public @Nullable GrTypeElement getTypeElement() {
    String typeText = getTypeText();
    if (typeText == null) return null;

    GrTypeElement typeElement = dereference(myTypeElement);
    if (typeElement == null) {
      typeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(typeText, getPsi());
      myTypeElement = new SoftReference<>(typeElement);
    }

    return typeElement;
  }
}
