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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

/**
 * @author ilyas
 */
public class GrClassInitializerImpl extends GroovyPsiElementImpl implements GrClassInitializer {

  public GrClassInitializerImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitClassInitializer(this);
  }

  public String toString() {
    return "Class initializer";
  }

  @Override
  @NotNull
  public GrOpenBlock getBlock() {
    return findNotNullChildByClass(GrOpenBlock.class);
  }

  @Override
  public boolean isStatic() {
    return getModifierList().hasExplicitModifier(PsiModifier.STATIC);
  }


  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass) {
        return (PsiClass) pparent;
      }
    }
    return null;
  }

  @Override
  public GrMember[] getMembers() {
    return new GrMember[]{this};
  }

  @Override
  @NotNull
  public GrModifierList getModifierList() {
    return findNotNullChildByClass(GrModifierList.class);
  }

  @Override
  public boolean hasModifierProperty(@GrModifier.GrModifierConstant @NonNls @NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  @Override
  public PsiCodeBlock getBody() {
    return PsiImplUtil.getOrCreatePsiCodeBlock(getBlock());
  }
}
