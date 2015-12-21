/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.spock;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class SpockMemberContributor extends NonCodeMembersContributor {

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull PsiElement place,
                                     @NotNull ResolveState state) {
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (ResolveUtil.shouldProcessProperties(classHint)) {
      GrMethod method = PsiTreeUtil.getParentOfType(place, GrMethod.class);
      if (method == null) return;

      if (aClass != method.getContainingClass()) return;

      Map<String, SpockVariableDescriptor> cachedValue = SpockUtils.getVariableMap(method);

      String nameHint = ResolveUtil.getNameHint(processor);
      if (nameHint == null) {
        for (SpockVariableDescriptor spockVar : cachedValue.values()) {
          if (!processor.execute(spockVar.getVariable(), state)) return;
        }
      }
      else {
        SpockVariableDescriptor spockVar = cachedValue.get(nameHint);
        if (spockVar != null && spockVar.getNavigationElement() != place) {
          if (!processor.execute(spockVar.getVariable(), state)) return;
        }
      }
    }

    if (ResolveUtil.shouldProcessMethods(classHint)) {
      String nameHint = ResolveUtil.getNameHint(processor);
      if (nameHint == null) {
        nameHint = place instanceof GrReferenceExpression ? ((GrReferenceExpression)place).getReferenceName() : null;
        if (nameHint != null) nameHint = GroovyPropertyUtils.getGetterNameNonBoolean(nameHint);
      }
      if ("get_".equals(nameHint)) {
        GrLightMethodBuilder m = new GrLightMethodBuilder(aClass.getManager(), "get_");
        m.setReturnType(null);
        if (!processor.execute(m, state)) return;
      }
    }
  }

  @Override
  public String getParentClassName() {
    return SpockUtils.SPEC_CLASS_NAME;
  }
}
