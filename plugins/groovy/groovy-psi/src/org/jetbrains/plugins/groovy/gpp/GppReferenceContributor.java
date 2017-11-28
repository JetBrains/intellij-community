/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gpp;

import com.intellij.openapi.util.Pair;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.List;

/**
 * @author peter
 */
public class GppReferenceContributor extends PsiReferenceContributor {

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(GrArgumentLabel.class), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        final PsiElement parent = element.getParent();
        if (parent instanceof GrNamedArgument && parent.getParent() instanceof GrListOrMap) {
          return new PsiReference[]{new GppMapMemberReference(element)};
        }
        return PsiReference.EMPTY_ARRAY;
      }
    });
  }

  public static class GppMapMemberReference extends PsiReferenceBase.Poly<GrArgumentLabel> {

    public GppMapMemberReference(PsiElement element) {
      super((GrArgumentLabel)element);
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
      return element instanceof PsiMethod && super.isReferenceTo(element);
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
      final GrArgumentLabel context = getElement();
      final GrNamedArgument namedArgument = (GrNamedArgument) context.getParent();
      final GrExpression map = (GrExpression)namedArgument.getParent();
      final PsiClassType classType = LiteralConstructorReference.getTargetConversionType(map);
      if (classType != null) {
        final PsiClass psiClass = classType.resolve();
        if (psiClass != null) {
          final GrExpression value = namedArgument.getExpression();

          final List<ResolveResult> applicable = addMethodCandidates(classType, value);

          final String memberName = getValue();
          if (value == null || applicable.isEmpty()) {
            final PsiMethod setter = PropertyUtilBase.findPropertySetter(psiClass, memberName, false, true);
            if (setter != null) {
              applicable.add(new PsiElementResolveResult(setter));
            } else {
              final PsiField field = PropertyUtilBase.findPropertyField(psiClass, memberName, false);
              if (field != null) {
                applicable.add(new PsiElementResolveResult(field));
              }
            }
          }

          return applicable.toArray(new ResolveResult[applicable.size()]);
        }
      }

      return ResolveResult.EMPTY_ARRAY;
    }


    private List<ResolveResult> addMethodCandidates(PsiClassType classType, GrExpression value) {
      PsiType valueType = value == null ? null : value.getType();
      final List<ResolveResult> applicable = ContainerUtil.newArrayList();

      if (value == null || InheritanceUtil.isInheritor(valueType, GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
        final List<ResolveResult> byName = ContainerUtil.newArrayList();
        for (Pair<PsiMethod, PsiSubstitutor> variant : GppClosureParameterTypeProvider.getMethodsToOverrideImplementInInheritor(classType, false)) {
          final PsiMethod method = variant.first;
          if (getValue().equals(method.getName())) {
            final ResolveResult resolveResult = new PsiElementResolveResult(method);
            byName.add(resolveResult);
            if (valueType instanceof GrClosureType) {
              final PsiType[] psiTypes = GppClosureParameterTypeProvider.getParameterTypes(variant);
              if (GppTypeConverter.isClosureOverride(psiTypes, (GrClosureType)valueType, myElement)) {
                applicable.add(resolveResult);
              }
            }
          }
        }

        if (applicable.isEmpty()) {
          return byName;
        }
      }
      return applicable;
    }

    @Override
    @NotNull
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY; //todo
    }
  }
}
