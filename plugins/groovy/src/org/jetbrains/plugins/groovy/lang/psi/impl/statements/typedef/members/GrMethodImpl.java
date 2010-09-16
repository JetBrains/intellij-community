/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members;

import com.intellij.lang.ASTNode;
import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;

import java.util.Set;

/**
 * @author Dmitry.Krasilschikov
 * @date 26.03.2007
 */

public class GrMethodImpl extends GrMethodBaseImpl<GrMethodStub> implements GrMethod, StubBasedPsiElement<GrMethodStub> {
  public GrMethodImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrMethodImpl(GrMethodStub stub) {
    super(stub, GroovyElementTypes.METHOD_DEFINITION);
  }

  public String toString() {
    return "Method";
  }

  @NotNull
  @Override
  public String[] getNamedParametersArray() {
    final GrMethodStub stub = getStub();
    if (stub != null) {
      return stub.getNamedParameters();
    }
    return super.getNamedParametersArray();
  }

  @NotNull
  @Override
  public String getName() {
    final GrMethodStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return super.getName();
  }
}