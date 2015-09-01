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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

import java.util.Map;

import static com.intellij.psi.PsiModifier.ABSTRACT;

/**
 * Created by Max Medvedev on 16/05/14
 */
public class GrTraitUtil {
  private static final Logger LOG = Logger.getInstance(GrTraitUtil.class);
  private static final PsiTypeMapper ID_MAPPER = new PsiTypeMapper() {
    @Override
    public PsiType visitClassType(PsiClassType classType) {
      return classType;
    }
  };

  @Contract("null -> false")
  public static boolean isInterface(@Nullable PsiClass aClass) {
    return aClass != null && aClass.isInterface() && !isTrait(aClass);
  }

  public static boolean isMethodAbstract(PsiMethod method) {
    if (method.getModifierList().hasExplicitModifier(ABSTRACT)) return true;

    PsiClass aClass = method.getContainingClass();
    return isInterface(aClass);
  }

  @NotNull
  public static String getTraitFieldPrefix(@NotNull PsiClass aClass) {
    String qname = aClass.getQualifiedName();
    LOG.assertTrue(qname != null, aClass.getClass());

    String[] idents = qname.split("\\.");

    StringBuilder buffer = new StringBuilder();
    for (String ident : idents) {
      buffer.append(ident).append("_");
    }

    buffer.append("_");
    return buffer.toString();
  }

  @Contract("null -> false")
  public static boolean isTrait(@Nullable PsiClass containingClass) {
    return containingClass instanceof GrTypeDefinition && ((GrTypeDefinition)containingClass).isTrait()
           || containingClass instanceof ClsClassImpl
              && containingClass.isInterface()
              && AnnotationUtil.isAnnotated(containingClass, "groovy.transform.Trait", false);
  }

  public static PsiMethod createTraitMethodFromCompiledHelperMethod(final PsiMethod compiledMethod, final PsiClass trait) {
    assert compiledMethod.getParameterList().getParametersCount() > 0;

    final GrLightMethodBuilder result = new GrLightMethodBuilder(compiledMethod.getManager(), compiledMethod.getName());
    result.setNavigationElement(compiledMethod);
    result.setOriginInfo("via @Trait");
    result.addModifier(PsiModifier.STATIC);
    for (PsiTypeParameter parameter : compiledMethod.getTypeParameters()) {
      result.getTypeParameterList().addParameter(parameter);
    }

    final PsiTypeVisitor<PsiType> corrector = createCorrector(compiledMethod, trait);

    final PsiParameter[] methodParameters = compiledMethod.getParameterList().getParameters();
    for (int i = 1; i < methodParameters.length; i++) {
      final PsiParameter originalParameter = methodParameters[i];
      final PsiType correctedType = originalParameter.getType().accept(corrector);
      result.addParameter(originalParameter.getName(), correctedType, false);
    }

    for (PsiClassType type : compiledMethod.getThrowsList().getReferencedTypes()) {
      final PsiType correctedType = type.accept(corrector);
      result.getThrowsList().addReference(correctedType instanceof PsiClassType ? (PsiClassType)correctedType : type);
    }

    {
      final PsiType originalType = compiledMethod.getReturnType();
      result.setReturnType(originalType == null ? null : originalType.accept(corrector));
    }

    return result;
  }

  @NotNull
  private static PsiTypeMapper createCorrector(final PsiMethod compiledMethod, final PsiClass trait) {
    final PsiTypeParameter[] traitTypeParameters = trait.getTypeParameters();
    if (traitTypeParameters.length == 0) return ID_MAPPER;

    final Map<String, PsiTypeParameter> substitutionMap = ContainerUtil.newTroveMap();
    for (PsiTypeParameter parameter : traitTypeParameters) {
      substitutionMap.put(parameter.getName(), parameter);
    }

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(trait.getProject()).getElementFactory();
    return new PsiTypeMapper() {

      @Nullable
      @Override
      public PsiType visitClassType(PsiClassType originalType) {
        final PsiClass resolved = originalType.resolve();
        // if resolved to method parameter -> return as is
        if (resolved instanceof PsiTypeParameter && compiledMethod.equals(((PsiTypeParameter)resolved).getOwner())) return originalType;
        final PsiType[] typeParameters = originalType.getParameters();
        final PsiTypeParameter byName = substitutionMap.get(originalType.getCanonicalText());
        if (byName != null) {
          assert typeParameters.length == 0;
          return elementFactory.createType(byName);
        }
        if (resolved == null) return originalType;
        if (typeParameters.length == 0) return originalType;    // do not go deeper

        final Ref<Boolean> hasChanges = Ref.create(false);
        final PsiTypeVisitor<PsiType> $this = this;
        final PsiType[] substitutes = ContainerUtil.map2Array(typeParameters, PsiType.class, new Function<PsiType, PsiType>() {
          @Override
          public PsiType fun(PsiType type) {
            final PsiType mapped = type.accept($this);
            hasChanges.set(mapped != type);
            return mapped;
          }
        });
        return hasChanges.get() ? elementFactory.createType(resolved, substitutes) : originalType;
      }
    };
  }
}
