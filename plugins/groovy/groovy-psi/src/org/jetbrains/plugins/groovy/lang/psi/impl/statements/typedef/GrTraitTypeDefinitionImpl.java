// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTraitTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.List;

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
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitTraitDefinition(this);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!super.processDeclarations(processor, state, lastParent, place)) return false;

    ElementClassHint hint = processor.getHint(ElementClassHint.KEY);
    if (!ResolveUtil.shouldProcessMethods(hint) && !ResolveUtil.shouldProcessProperties(hint)) return true;

    List<PsiClass> classes = GrTraitUtil.getSelfTypeClasses(this);
    for (PsiClass clazz : classes) {
      if (!clazz.processDeclarations(processor, state, lastParent, place)) return false;
    }
    return true;
  }
}
