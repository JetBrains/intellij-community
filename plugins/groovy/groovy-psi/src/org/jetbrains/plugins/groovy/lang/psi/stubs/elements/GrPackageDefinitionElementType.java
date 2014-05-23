/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging.GrPackageDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrPackageDefinitionStub;

import java.io.IOException;

public class GrPackageDefinitionElementType extends GrStubElementType<GrPackageDefinitionStub, GrPackageDefinition> {
  public GrPackageDefinitionElementType(@NonNls @NotNull String debugName) {
    super(debugName);
  }

  @Override
  public GrPackageDefinition createPsi(@NotNull GrPackageDefinitionStub stub) {
    return new GrPackageDefinitionImpl(stub);
  }

  @Override
  public GrPackageDefinitionStub createStub(@NotNull GrPackageDefinition psi, StubElement parentStub) {
    return new GrPackageDefinitionStub(parentStub, GroovyElementTypes.PACKAGE_DEFINITION, StringRef.fromString(psi.getPackageName()));
  }

  @Override
  public void serialize(@NotNull GrPackageDefinitionStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getPackageName());
  }

  @NotNull
  @Override
  public GrPackageDefinitionStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new GrPackageDefinitionStub(parentStub, GroovyElementTypes.PACKAGE_DEFINITION, dataStream.readName());
  }
}
