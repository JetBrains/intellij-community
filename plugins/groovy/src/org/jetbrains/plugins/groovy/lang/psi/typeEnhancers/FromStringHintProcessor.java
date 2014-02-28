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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.*;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Created by Max Medvedev on 28/02/14
 */
public class FromStringHintProcessor extends SignatureHintProcessor {

  @Override
  public String getHintName() {
    return "groovy.transform.stc.FromString";
  }

  @NotNull
  @Override
  public List<PsiType[]> inferExpectedSignatures(@NotNull final PsiMethod method,
                                                 @NotNull final PsiSubstitutor substitutor,
                                                 @Nullable PsiAnnotationMemberValue options) {
    if (!(options instanceof PsiArrayInitializerMemberValue)) return Collections.emptyList();

    PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)options).getInitializers();

    return Collections.singletonList(ContainerUtil.map(initializers, new Function<PsiAnnotationMemberValue, PsiType>() {
      @Override
      public PsiType fun(PsiAnnotationMemberValue memberValue) {
        if (memberValue instanceof PsiLiteral) {
          Object value = ((PsiLiteral)memberValue).getValue();
          if (value instanceof String) {
            try {
              PsiType original = JavaPsiFacade.getElementFactory(memberValue.getProject()).createTypeFromText((String)value, method);
              return substitutor.substitute(original);
            }
            catch (IncorrectOperationException e) {
              //do nothing. Just don't throw an exception
            }
          }
        }

        return PsiType.NULL;
      }
    }, new PsiType[initializers.length]));
  }
}
