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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTraitTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

/**
 * Created by Max Medvedev on 09/04/14
 */
public class GrTraitTypeDefinitionImpl extends GrTypeDefinitionImpl implements GrTraitTypeDefinition {

  public GrTraitTypeDefinitionImpl(GrTypeDefinitionStub stub) {
    super(stub, GroovyElementTypes.TRAIT_DEFINITION);
  }

  public GrTraitTypeDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public boolean isTrait() {
    return true;
  }

  @Override
  public boolean isInterface() {
    return true;
  }

  @Override
  public String toString() {
    return "Trait definition";
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTraitDefinition(this);
  }
}
