/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrModifierListStub;

import java.io.IOException;

/**
 * @author Maxim.Medvedev
 */
public class GrModifierListElementType extends GrStubElementType<GrModifierListStub, GrModifierList> {
  public GrModifierListElementType(String debugName) {
    super(debugName);
  }

  @Override
  public GrModifierList createPsi(@NotNull GrModifierListStub stub) {
    return new GrModifierListImpl(stub);
  }

  @Override
  public GrModifierListStub createStub(@NotNull GrModifierList psi, StubElement parentStub) {
    return new GrModifierListStub(parentStub, GroovyElementTypes.MODIFIERS, GrModifierListStub.buildFlags(psi));
  }

  public void serialize(@NotNull GrModifierListStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeVarInt(stub.getModifiersFlags());
  }

  @NotNull
  public GrModifierListStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new GrModifierListStub(parentStub, GroovyElementTypes.MODIFIERS, dataStream.readVarInt());
  }

}
