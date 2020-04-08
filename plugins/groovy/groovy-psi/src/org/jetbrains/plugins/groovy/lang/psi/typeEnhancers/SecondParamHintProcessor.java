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

import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class SecondParamHintProcessor extends ParamHintProcessor {
  public SecondParamHintProcessor() {
    super("groovy.transform.stc.SecondParam", 1, -1);
  }

  public static class FirstGeneric extends ParamHintProcessor {
    public FirstGeneric() {
      super("groovy.transform.stc.SecondParam.FirstGenericType", 1, 0);
    }
  }

  public static class SecondGeneric extends ParamHintProcessor {
    public SecondGeneric() {
      super("groovy.transform.stc.SecondParam.SecondGenericType", 1, 1);
    }
  }

  public static class ThirdGeneric extends ParamHintProcessor {
    public ThirdGeneric() {
      super("groovy.transform.stc.SecondParam.ThirdGenericType", 1, 2);
    }
  }

  public static class Component extends SignatureHintProcessor {
    @Override
    public String getHintName() {
      return "groovy.transform.stc.SecondParam.Component";
    }

    @NotNull
    @Override
    public List<PsiType[]> inferExpectedSignatures(@NotNull PsiMethod method,
                                                   @NotNull PsiSubstitutor substitutor,
                                                   String @NotNull [] options) {
      List<PsiType[]> signatures = new SecondParamHintProcessor().inferExpectedSignatures(method, substitutor, options);
      if (signatures.size() == 1) {
        PsiType[] signature = signatures.get(0);
        if (signature.length == 1) {
          PsiType type = signature[0];
          if (type instanceof PsiArrayType) {
            return produceResult(((PsiArrayType)type).getComponentType());
          }
        }
      }
      return Collections.emptyList();
    }
  }

}
