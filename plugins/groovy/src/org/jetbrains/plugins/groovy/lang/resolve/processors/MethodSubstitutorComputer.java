/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

/**
 * @author Max Medvedev
 */
public class MethodSubstitutorComputer extends SubstitutorComputer {

  public MethodSubstitutorComputer(PsiType thisType,
                                   PsiType[] argumentTypes,
                                   PsiType[] typeArguments,
                                   boolean allVariants,
                                   GroovyPsiElement place) {
    super(thisType, argumentTypes, typeArguments, allVariants, place);
  }

  @Override
  @Nullable
  protected PsiType getContextType() {
    PsiElement call = myPlace.getParent();
    final PsiElement parent = call.getParent();
    if (parent instanceof GrReturnStatement || exitsContains(call)) {
      final GrMethod method = PsiTreeUtil.getParentOfType(parent, GrMethod.class, true, GrClosableBlock.class);
      if (method != null) {
        return method.getReturnType();
      }
    }
    else if (parent instanceof GrAssignmentExpression && myPlace.getParent().equals(((GrAssignmentExpression)parent).getRValue())) {
      return ((GrAssignmentExpression)parent).getLValue().getType();
    }
    else if (parent instanceof GrVariable) {
      return ((GrVariable)parent).getDeclaredType();
    }
    return null;
  }
}
