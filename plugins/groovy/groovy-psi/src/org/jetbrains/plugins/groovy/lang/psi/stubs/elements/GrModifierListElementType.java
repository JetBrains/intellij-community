// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
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

  @NotNull
  @Override
  public GrModifierListStub createStub(@NotNull GrModifierList psi, StubElement parentStub) {
    return new GrModifierListStub(parentStub, GroovyStubElementTypes.MODIFIER_LIST, psi.getModifierFlags());
  }

  @Override
  public void serialize(@NotNull GrModifierListStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeVarInt(stub.getModifiersFlags());
  }

  @Override
  @NotNull
  public GrModifierListStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new GrModifierListStub(parentStub, GroovyStubElementTypes.MODIFIER_LIST, dataStream.readVarInt());
  }

}
