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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightClassReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

/**
 * @author Maxim.Medvedev
 */
public class GrEnumConstantInitializerImpl extends GrAnonymousClassDefinitionImpl implements GrEnumConstantInitializer {
  private static final Logger LOG = Logger.getInstance(GrEnumConstantInitializerImpl.class);

  public GrEnumConstantInitializerImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrEnumConstantInitializerImpl(GrTypeDefinitionStub stub) {
    super(stub, GroovyElementTypes.ENUM_CONSTANT_INITIALIZER);
  }

  @NotNull
  @Override
  public GrEnumConstant getEnumConstant() {
    return (GrEnumConstant)getParent();
  }

  @Override
  public boolean isInQualifiedNew() {
    return false;
  }

  @NotNull
  @Override
  public GrCodeReferenceElement getBaseClassReferenceGroovy() {
    return new GrLightClassReferenceElement(getBaseClass(), this);
  }

  private PsiClass getBaseClass() {
    PsiElement parent = getParent();
    LOG.assertTrue(parent instanceof GrEnumConstant);
    PsiClass containingClass = ((GrEnumConstant)parent).getContainingClass();
    LOG.assertTrue(containingClass != null);
    return containingClass;
  }

  @NotNull
  @Override
  public PsiElement getNameIdentifierGroovy() {
    return getEnumConstant().getNameIdentifierGroovy();
  }

  @Nullable
  @Override
  public GrArgumentList getArgumentListGroovy() {
    return getEnumConstant().getArgumentList();
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  @Override
  public boolean isEnum() {
    return false;
  }

  @Override
  public GrTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  public PsiElement getOriginalElement() {
    return this;
  }

  @Override
  public String toString() {
    return "Enum constant initializer";
  }


}
