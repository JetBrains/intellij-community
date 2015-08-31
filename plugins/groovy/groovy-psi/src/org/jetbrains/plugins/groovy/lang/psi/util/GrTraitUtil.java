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

  public static PsiMethod createTraitMethodFromCompiledHelperMethod(final PsiMethod compiledMethod, PsiClass trait) {
    assert compiledMethod.getParameterList().getParametersCount() > 0;

    final GrLightMethodBuilder result = new GrLightMethodBuilder(compiledMethod.getManager(), compiledMethod.getName());
    result.setNavigationElement(compiledMethod);
    result.setOriginInfo("via @Trait");
    result.addModifier(PsiModifier.STATIC);
    for (PsiTypeParameter parameter : compiledMethod.getTypeParameters()) {
      result.getTypeParameterList().addParameter(parameter);
    }

    final Map<String, PsiTypeParameter> substitutionMap = ContainerUtil.newTroveMap();
    for (PsiTypeParameter parameter : trait.getTypeParameters()) {
      substitutionMap.put(parameter.getName(), parameter);
    }

    final PsiElementFactory myElementFactory = JavaPsiFacade.getInstance(compiledMethod.getProject()).getElementFactory();
    final PsiTypeVisitor<PsiType> corrector = new PsiTypeMapper() {

      @Nullable
      @Override
      public PsiType visitClassType(PsiClassType classType) {
        final PsiClass resolved = classType.resolve();
        // if resolved to method parameter -> return as is
        if (resolved instanceof PsiTypeParameter && compiledMethod.equals(((PsiTypeParameter)resolved).getOwner())) return classType;
        if (resolved == null) {
          // if not resolved -> try to get from map
          final PsiTypeParameter byName = substitutionMap.get(classType.getCanonicalText());
          return byName == null ? classType : myElementFactory.createType(byName);
        }
        else {
          // if resolved -> get from map anyways
          final PsiTypeParameter byName = substitutionMap.get(resolved.getName());
          final PsiTypeVisitor<PsiType> $this = this;
          final PsiType[] substitutes = !classType.hasParameters() ? PsiType.EMPTY_ARRAY : ContainerUtil.map2Array(
            classType.getParameters(), PsiType.class, new Function<PsiType, PsiType>() {
              @Override
              public PsiType fun(PsiType type) {
                return type.accept($this);
              }
            }
          );
          return myElementFactory.createType(byName != null ? byName : resolved, substitutes);
        }
      }
    };

    for (int i = 1; i < compiledMethod.getParameterList().getParameters().length; i++) {
      final PsiParameter originalParameter = compiledMethod.getParameterList().getParameters()[i];
      final PsiType originalType = originalParameter.getType();
      final PsiType correctedType = trait.hasTypeParameters() ? originalType.accept(corrector) : originalType;
      result.addParameter(originalParameter.getName(), correctedType, false);
    }

    for (PsiClassType type : compiledMethod.getThrowsList().getReferencedTypes()) {
      final PsiType correctedType = trait.hasTypeParameters() ? type.accept(corrector) : type;
      result.getThrowsList().addReference(correctedType instanceof PsiClassType ? (PsiClassType)correctedType : type);
    }

    {
      final PsiType originalType = compiledMethod.getReturnType();
      result.setReturnType(originalType != null && trait.hasTypeParameters() ? originalType.accept(corrector) : originalType);
    }

    return result;
  }
}
