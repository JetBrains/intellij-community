// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyEmptyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrRecordDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

public class GrRecordDefinitionImpl extends GrTypeDefinitionImpl implements GrRecordDefinition {

  private static final Logger LOG = Logger.getInstance(GrRecordDefinitionImpl.class);

  public GrRecordDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrRecordDefinitionImpl(final GrTypeDefinitionStub stub) {
    super(stub, GroovyStubElementTypes.CLASS_TYPE_DEFINITION);
  }

  @Override
  public String toString() {
    return "Class definition";
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitRecordDefinition(this);
  }

  @Override
  public GrParameter @NotNull [] getParameters() {
    return this.getParameterList().getParameters();
  }

  @Override
  public @NotNull GrParameterList getParameterList() {
    final GrParameterList parameterList = getStubOrPsiChild(GroovyEmptyStubElementTypes.PARAMETER_LIST);
    LOG.assertTrue(parameterList != null);
    return parameterList;  }

  @Override
  public boolean isVarArgs() {
    return PsiImplUtil.isVarArgs(getParameters());
  }
}
