/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.Collection;

/**
 * @author Max Medvedev
 */
public class ClosureToSamConverter extends GrTypeConverter {
  private static final LightCacheKey<Ref<MethodSignature>> SAM_SIGNATURE_LIGHT_CACHE_KEY = LightCacheKey.createByJavaModificationCount();

  @Override
  public boolean isAllowedInMethodCall() {
    return true;
  }

  @Override
  public Boolean isConvertible(@NotNull PsiType ltype, @NotNull PsiType rtype, @NotNull final GroovyPsiElement context) {
    if (rtype instanceof GrClosureType && ltype instanceof PsiClassType && GroovyConfigUtils.getInstance().isVersionAtLeast(context, GroovyConfigUtils.GROOVY2_2)) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)ltype).resolveGenerics();
      final PsiClass resolved = resolveResult.getElement();
      if (resolved != null) {
        final MethodSignature signature = findSingleAbstractMethodClass(resolved, resolveResult.getSubstitutor());
        if (signature != null) {

          final PsiType[] samParameterTypes = signature.getParameterTypes();

          GrSignature closureSignature = ((GrClosureType)rtype).getSignature();
          if (GrClosureSignatureUtil.isSignatureApplicable(closureSignature, samParameterTypes, context)) {
            return true;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static MethodSignature findSingleAbstractMethodClass(@NotNull PsiClass aClass,
                                                               @NotNull PsiSubstitutor substitutor) {
    MethodSignature signature;
    Ref<MethodSignature> cached = SAM_SIGNATURE_LIGHT_CACHE_KEY.getCachedValue(aClass);
    if (cached != null) {
      signature = cached.get();
    }
    else {
      cached = Ref.create(doFindSingleAbstractMethodClass(aClass));
      signature = SAM_SIGNATURE_LIGHT_CACHE_KEY.putCachedValue(aClass, cached).get();
    }

    return signature != null ? substitute(signature, substitutor): null;
  }

  @Nullable
  private static MethodSignature doFindSingleAbstractMethodClass(@NotNull PsiClass aClass) {
    Collection<MethodSignature> toImplement = OverrideImplementExploreUtil.getMethodSignaturesToImplement(aClass);
    if (toImplement.size() > 1) return null;

    MethodSignature abstractSignature = toImplement.isEmpty() ? null : toImplement.iterator().next();
    for (PsiMethod method : aClass.getMethods()) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        if (abstractSignature != null) return null;
        abstractSignature = method.getSignature(PsiSubstitutor.EMPTY);
      }
    }

    return abstractSignature;
  }

  @NotNull
  private static MethodSignature substitute(@NotNull MethodSignature signature, @NotNull PsiSubstitutor substitutor) {
    return MethodSignatureUtil.createMethodSignature(signature.getName(), signature.getParameterTypes(), PsiTypeParameter.EMPTY_ARRAY, substitutor, false);
  }
}
