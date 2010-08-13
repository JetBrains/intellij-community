/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 16, 2010
 */
public class AntDomDefaultTargetConverter extends Converter<Trinity<AntDomTarget, String, Map<String, AntDomTarget>>> implements CustomReferenceConverter<Trinity<AntDomTarget, String, Map<String, AntDomTarget>>>{

  @NotNull public PsiReference[] createReferences(final GenericDomValue<Trinity<AntDomTarget, String, Map<String, AntDomTarget>>> value, PsiElement element, ConvertContext context) {
    return new PsiReference[] {new AntDomReferenceBase(element, true) {
      public PsiElement resolve() {
        final Trinity<AntDomTarget, String, Map<String, AntDomTarget>> trinity = value.getValue();
        if (trinity == null) {
          return null;
        }
        final DomTarget domTarget = trinity.getFirst() != null? DomTarget.getTarget(trinity.getFirst()) : null;
        return domTarget != null? PomService.convertToPsi(domTarget) : null;
      }

      @NotNull
      public Object[] getVariants() {
        final Trinity<AntDomTarget, String, Map<String, AntDomTarget>> trinity = value.getValue();
        if (trinity == null) {
          return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }
        final Set<String> set = trinity.getThird().keySet();
        return set.toArray(new Object[set.size()]);
      }

      public String getUnresolvedMessagePattern() {
        return AntBundle.message("cannot.resolve.target", getCanonicalText());
      }
    }};
  }

  @Nullable
  public Trinity<AntDomTarget, String, Map<String, AntDomTarget>> fromString(@Nullable @NonNls String s, ConvertContext context) {
    final AntDomElement element = AntSupport.getInvocationAntDomElement(context);
    if (element != null && s != null) {
      final AntDomProject project = element.getAntProject();
      final TargetResolver.Result result = TargetResolver.resolve(project.getContextAntProject(), null, s);
      final Pair<AntDomTarget,String> pair = result.getResolvedTarget(s);
      return new Trinity<AntDomTarget, String, Map<String, AntDomTarget>>(pair != null? pair.getFirst() : null, pair != null? pair.getSecond() : null, result.getVariants());
    }
    return null;
  }

  @Nullable
  public String toString(@Nullable Trinity<AntDomTarget, String, Map<String, AntDomTarget>> trinity, ConvertContext context) {
    return trinity != null? trinity.getSecond() : null;
  }
}
