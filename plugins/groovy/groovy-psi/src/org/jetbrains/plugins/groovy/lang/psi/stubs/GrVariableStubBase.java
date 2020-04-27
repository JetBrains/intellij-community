/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.reference.SoftReference;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

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

  @Nullable
  @Override
  public String getName() {
    return StringRef.toString(myNameRef);
  }

  public String @NotNull [] getAnnotations() {
    return myAnnotations;
  }

  @Nullable
  public String getTypeText() {
    return myTypeText;
  }

  @Nullable
  public GrTypeElement getTypeElement() {
    String typeText = getTypeText();
    if (typeText == null) return null;

    GrTypeElement typeElement = SoftReference.dereference(myTypeElement);
    if (typeElement == null) {
      typeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(typeText, getPsi());
      myTypeElement = new SoftReference<>(typeElement);
    }

    return typeElement;
  }
}
