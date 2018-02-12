/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

public class GrVariableDeclarationStub extends StubBase<GrVariableDeclaration> {

  private final @Nullable String myTypeString;

  private volatile SoftReference<GrTypeElement> myTypeElement;

  public GrVariableDeclarationStub(StubElement parent, @Nullable String typeString) {
    super(parent, GroovyElementTypes.VARIABLE_DEFINITION);
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
