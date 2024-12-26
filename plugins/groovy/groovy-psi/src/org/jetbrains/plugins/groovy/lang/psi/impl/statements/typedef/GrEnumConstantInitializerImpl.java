// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
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
    super(stub, GroovyStubElementTypes.ENUM_CONSTANT_INITIALIZER);
  }

  @Override
  public @NotNull GrEnumConstant getEnumConstant() {
    return (GrEnumConstant)getParent();
  }

  @Override
  public @NotNull GrCodeReferenceElement getBaseClassReferenceGroovy() {
    return new GrLightClassReferenceElement(getBaseClass(), this);
  }

  private PsiClass getBaseClass() {
    PsiElement parent = getParent();
    LOG.assertTrue(parent instanceof GrEnumConstant);
    PsiClass containingClass = ((GrEnumConstant)parent).getContainingClass();
    LOG.assertTrue(containingClass != null);
    return containingClass;
  }

  @Override
  public @NotNull PsiElement getNameIdentifierGroovy() {
    return getEnumConstant().getNameIdentifierGroovy();
  }

  @Override
  public @Nullable GrArgumentList getArgumentListGroovy() {
    return getEnumConstant().getArgumentList();
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
