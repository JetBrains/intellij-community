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
import com.intellij.psi.PsiClassType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnnotationTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

/**
 * @author Dmitry.Krasilschikov
 * @date 18.03.2007
 */
public class GrAnnotationTypeDefinitionImpl extends GrTypeDefinitionImpl implements GrAnnotationTypeDefinition {
  public GrAnnotationTypeDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrAnnotationTypeDefinitionImpl(GrTypeDefinitionStub stub) {
    super(stub, GroovyElementTypes.ANNOTATION_DEFINITION);
  }

  public String toString() {
    return "Annotation definition";
  }

  @Override
  public boolean isAnnotationType() {
    return true;
  }

  @Override
  public boolean isInterface() {
    return true;
  }

  @Override
  protected String[] getExtendsNames() {
    return new String[]{"Annotation"};
  }

  @Override
  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return new PsiClassType[]{createAnnotationType()};
  }

  private PsiClassType createAnnotationType() {
    return TypesUtil.createTypeByFQClassName("java.lang.annotation.Annotation", this);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAnnotationTypeDefinition(this);
  }
}
